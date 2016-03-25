(defproject cljs-mancy "1.0.0"
  :description "cljs repl for mancy"
  :url "http://mancy-re.pl"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.8.40"]
                 [org.clojure/tools.reader "1.0.0-alpha3"]
                 [replumb "0.2.1"]]
  :source-paths ["src"]  
  :target-path "out/%s"
  :min-lein-version "2.5.3"
  :plugins [[lein-cljsbuild "1.1.2"]]
  :cljsbuild {:builds [{:id "mancy-repl"
                        :source-paths ["src"]
                        :compiler {:target :nodejs
                                   :optimizations :simple
                                   :output-to "out/cljs_mancy.js"
                                   :static-fns true
                                   :hashbang false
                                   :language-out :ecmascript5
                                   :elide-asserts true
                                   :optimize-constants true
                                   :cache-analysis true
                                   :parallel-build true}}]})