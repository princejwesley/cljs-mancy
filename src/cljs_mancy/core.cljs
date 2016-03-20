(ns cljs-mancy.core
  "REPL for mancy."
  (:require-macros [cljs.core]
                   [cljs.env.macros :refer [ensure with-compiler-env]]
                   [cljs.analyzer.macros :refer [no-warn]])
  (:require [cljs.nodejs :as nodejs]
            [replumb.core :as replumb]
            [replumb.ast :as ast] 
            [replumb.repl :refer [make-load-fn
                                  make-js-eval-fn
                                  repl-special?
                                  read-string
                                  process-repl-special
                                  process-in-ns]]
            [replumb.cache :as cache]
            [cljs-mancy.io :as io]
            [cljs-mancy.hint :as hint :refer [process-apropos]]
            [clojure.string :as s]
            [cljs.tagged-literals :as tags]
            [cljs.js :as jsc]))

;; mancy will override it
(nodejs/enable-util-print!)

;; todo keep environment together
(defonce cenv (jsc/empty-state))
(defonce copts (atom { :source-map false
              :load-macros true
              :analyze-deps true
              :static-fns false
              :def-emits-var true
              :context :expr
              :read-file-fn! io/read-file!
              :src-paths []
              :ns 'cljs.user
            }))

(defn fake-eval-fn!
  "Copied from replumb -> 'make-js-eval-fn' and modified"
  [opts]
  (fn [{:keys [path name source cache]}]
    (let [cache-path (get-in opts [:cache :path])]
      (when (and path source cache cache-path)
        (let [cache-prefix-for-path (cache/cache-prefix-for-path cache-path
                                                                 path
                                                                 (cache/is-macros? cache))
              [js-path json-path] (map #(str cache-prefix-for-path  %) [".js" ".cache.json"])]
          (io/write-file! js-path (str (cache/compiled-by-string) "\n" source))
          (io/write-file! json-path (cache/cljs->transit-json cache)))) source)))

;; Modified version of replumb.repl/base-eval-opts!
(defn base-eval-opts
  ([]
   (base-eval-opts {}))
  ([user-opts]
   {:ns (:ns @copts)
    :context (or (:context @copts) :expr)
    :source-map false
    :def-emits-var true
    :load (or (:load user-opts) (make-load-fn @copts))
    :eval (or (:eval user-opts) (fake-eval-fn! @copts))
    :verbose false
    :static-fns false}))

(defn eval! [s] 
  (js/eval (clj->js (:source s))))

;; Modified version of replumb.repl/process-in-ns
(defn process-in-ns!
  [_ cb data ns-string]
  (jsc/eval
    cenv
    ns-string
    (base-eval-opts {:eval eval!})
    (fn [{:keys [error value] :as result}]
      (if error
        (cb {:error error})
        (let [ns-symbol value]
          (if-not (symbol? ns-symbol)
            (cb {:error (ex-info "Argument to 'in-ns' must be a symbol" {:tag ::error}) } nil)
            (if (some (partial = ns-symbol) (ast/known-namespaces cenv))
              (cb {:error nil :value nil :ns ns-symbol})
              (let [ns-form `(~'ns ~ns-symbol)]
                (jsc/eval
                  cenv
                  ns-form
                  (base-eval-opts {:eval eval!})
                  (fn [{:keys [error value]}]
                    (if error 
                     (cb {:error error})
                     (cb {:error nil :value value :ns ns-symbol}))))))))))))


(defn- callback!
  "Handle callback. Update ns"
  [cb expr]
  (fn [{:keys [error value ns]}]
    (if error (cb (clj->js error) nil) (do
      (when-not (nil? ns) (swap! copts assoc :ns ns))
      (cb nil (clj->js { :value value
                 :special (if (repl-special? expr) (first expr) nil)
               }))))))

(defn compile
  [source cb]
  (try
    (let [expr (read-string {:read-cond :allow :features #{:cljs}} source)
          on-return (callback! cb expr)]
      (if (repl-special? expr)
        (process-repl-special @copts on-return {:form expr :ns (:ns @copts) :target "nodejs"} expr)
        (jsc/eval-str*
             {:*compiler*     cenv
              :*data-readers* tags/*cljs-data-readers*
              :*analyze-deps* (:analyze-deps @copts true)
              :*load-macros*  (:load-macros @copts true)
              :*load-fn*      (make-load-fn @copts)
              :*eval-fn*      (fake-eval-fn! @copts)}
             source 'mancy-repl @copts on-return)))
    (catch js/Error err
      (cb (clj->js err))
      )))

(def clj2js clj->js)

(def js2clj js->clj)

(defn complete [text] (clj2js (process-apropos text @copts cenv)))

(defn add-paths [paths]
  (let [v? (vector? paths)
        p (if v? paths [paths])]
    (swap! copts assoc :src-paths (into (:src-paths @copts) p))))

(defn set-paths [paths]
  (let [v? (vector? paths)
        p (if v? paths [paths])]
    (swap! copts assoc :src-paths p)))


(set! make-js-eval-fn fake-eval-fn!)
(set! process-in-ns process-in-ns!)
(set! replumb.repl/base-eval-opts! base-eval-opts)
(defn current-ns [] (:ns @copts))

(set! *main-cli-fn* (fn [] nil))

