#!/bin/bash
rm -rf out/ && lein cljsbuild once mancy-repl
sed '1d' out/cljs_mancy.js > clojurescript.js
echo "module.exports = { compiler: cljs_mancy.core, goog: goog, cljs: cljs }" >> clojurescript.js
