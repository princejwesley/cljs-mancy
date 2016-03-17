(defproject cljs-mancy "1.0.0"
  :description "cljs repl for mancy"
  :url "http://mancy-re.pl"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.8.32"]
                 [replumb "0.1.5-3"]]
  :source-paths ["src/cljs-mancy"]  
  :main ^:skip-aot cljs-mancy.core
  :target-path "target/%s"
  :min-lein-version "2.5.3"
  :plugins []  
  :profiles {:uberjar {:aot :all}})
