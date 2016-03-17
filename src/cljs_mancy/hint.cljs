(ns cljs-mancy.hint
  "Copied from jaredly/reepl and tweaked"
  (:require [cljs.js :as jsc]
            [cljs.analyzer :as ana]
            [cljs.tools.reader]
            [replumb.ast :as ast]   
            [clojure.string :as str]))


(defn compare-completion
  "The comparison algo for completions

  1. if one is exactly the text, then it goes first
  2. if one *starts* with the text, then it goes first
  3. otherwise leave in current order"
  [text a b]
  (cond
    (and (= text a)
         (= text b)) 0
    (= text a) -1
    (= text b) 1
    :else
    (let [a-starts (= 0 (.indexOf a text))
          b-starts (= 0 (.indexOf b text))]
      (cond
        (and a-starts b-starts) 0
        a-starts -1
        b-starts 1
        :default 0))))

(defn compare-ns
  "Sorting algo for namespaces

  The current ns comes first, then cljs.core, then anything else
  alphabetically"
  [current ns1 ns2]
  (cond
    (= ns1 current) -1
    (= ns2 current) 1
    (= ns1 'cljs.core) -1
    (= ns2 'cljs.core) 1
    :default (compare ns1 ns2)))

(defn get-from-js-ns
  "Use js introspection to get a list of interns in a namespaces

  This is pretty dependent on cljs runtime internals, so it may break in the
  future (although I think it's fairly unlikely). It takes advantage of the fact
  that the ns `something.other.thing' is available as an object on
  `window.something.other.thing', and Object.keys gets all the variables in that
  namespace."
  [ns]

  (let [parts (map munge (.split (str ns) "."))
        ns (reduce aget js/global parts)]
    (if-not ns
      []
      (map demunge (js/Object.keys ns)))))

(defn dedup-requires
  "Takes a map of {require-name ns-name} and dedups multiple keys that have the
  same ns-name value."
  [requires]
  (first
   (reduce (fn [[result seen] [k v]]
            (if (seen v)
              [result seen]
              [(assoc result k v) (conj seen v)])) [{} #{}] requires)))

(defn get-matching-ns-interns [[name ns] matches? only-ns env]
  (let [ns-name (str ns)
        publics (keys (ast/ns-publics env ns))
        publics (if (empty? publics)
                  (get-from-js-ns ns)
                  publics)]
    (if-not (or (nil? only-ns)
                (= only-ns ns-name))
      []
      (sort (map #(symbol name (str %))
                 (filter matches?
                         publics))))))

(defn js-attrs [obj]
  (if-not obj
    []
    (let [constructor (.-constructor obj)
        proto (js/Object.getPrototypeOf obj)]
    (concat (js/Object.keys obj)
            (when-not (= proto obj)
              (js-attrs proto))))))

(defn js-completion
  [text]
  (let [parts (vec (.split text "."))
        completing (or (last parts) "")
        prefix #(str "js/" (str/join "." (conj (vec (butlast parts)) %)))
        possibles (js-attrs (reduce aget js/window (butlast parts)))]
    (->> possibles
         (filter #(not (= -1 (.indexOf % completing))))
         (sort (partial compare-completion text))
         (map #(-> [nil (prefix %) (prefix %)])))))

(defn cljs-completion
  "Tab completion. Copied w/ extensive modifications from replumb.repl/process-apropos."
  [text opts env]
  (let [[only-ns text] (if-not (= -1 (.indexOf text "/"))
                         (.split text "/")
                         [nil text])
        matches? #(and
                   ;; TODO find out what these t_cljs$core things are... seem to be nil
                   (= -1 (.indexOf (str %) "t_cljs$core"))
                   (< -1 (.indexOf (str %) text)))
        current-ns (:ns opts)
        replace-name (fn [sym]
                       (if (or
                            (= (namespace sym) "cljs.core")
                            (= (namespace sym) (str current-ns)))
                         (name sym)
                         (str sym)))
        requires (:requires
                  (ast/namespace env (:ns opts)))
        only-ns (when only-ns
                  (or (str (get requires (symbol only-ns)))
                      only-ns))
        requires (concat
                  [[nil current-ns]
                   [nil 'cljs.core]]
                  (dedup-requires (vec requires)))
        names (set (apply concat requires))
        defs (->> requires
                  (sort-by second (partial compare-ns current-ns))
                  (mapcat #(get-matching-ns-interns % matches? only-ns env))
                  ;; [qualified symbol, show text, replace text]
                  (map #(-> [% (str %) (replace-name %) (name %)]))
                  (sort-by #(get % 3) (partial compare-completion text)))]
    (vec (concat
          ;; TODO make this configurable
          (take 75 defs)
          (map
           #(-> [% (str %) (str %)])
           (filter matches? names))))))


(defn process-apropos [text opts env]
  (let [w (last (str/split text #"\s+"))]
    (if (= 0 (.indexOf w "js/"))
      (js-completion (.slice w 3))
      (cljs-completion w opts env))))
