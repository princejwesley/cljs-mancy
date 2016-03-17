(ns cljs-mancy.core
  "REPL for mancy"
  (:require-macros [cljs.core]
                   [cljs.env.macros :refer [ensure with-compiler-env]]
                   [cljs.analyzer.macros :refer [no-warn]])
  (:require [cljs.nodejs :as nodejs]
            [replumb.core :as replumb]
            [replumb.repl :refer [make-load-fn, make-js-eval-fn, repl-special?, read-string, process-repl-special]]
            [replumb.cache :as cache]
            [cljs-mancy.io :as io]
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

(defn- callback!
  "Handle callback. Update ns"
  [cb]
  (fn [{:keys [error value ns]}]
    (if error (cb (clj->js error) nil) (do (swap! copts assoc :ns ns) (clj->js value)) )))

(defn compile
  [source cb]
  (try
    (let [expr (read-string {:read-cond :allow :features #{:cljs}} source)
          on-return (callback! cb)]
      (if (repl-special? expr)
        (process-repl-special @copts on-return {:form expr :ns (:ns @copts) :target "nodejs"} expr)
        (jsc/eval-str*
             {:*compiler*     cenv
              :*data-readers* tags/*cljs-data-readers*
              :*analyze-deps* (:analyze-deps @copts true)
              :*load-macros*  (:load-macros @copts true)
              :*load-fn*      (or (:load @copts) make-load-fn)
              :*eval-fn*      (or (:eval @copts) (fake-eval-fn! @copts))}
             source 'mancy-repl @copts on-return)))
    (catch js/Error err
      (cb (clj->js err))
      )))

(def clj2js clj->js)

(def js2clj js->clj)

(set! make-js-eval-fn fake-eval-fn!)

(set! *main-cli-fn* (fn [] nil))

