#!/bin/bash
#run from app root
rm -rf dist/ rm -rf out/ && lein cljsbuild once mancy-repl
mkdir dist
sed '1d' out/cljs_mancy.js > dist/clojurescript.js
echo "module.exports = { compiler: cljs_mancy.core, goog: goog, cljs: cljs }" >> dist/clojurescript.js
mv out/*/*/cljs dist
mv out/*/*/clojure dist

