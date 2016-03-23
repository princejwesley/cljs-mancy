#!/bin/bash
#run from app root
rm -rf out/ && lein cljsbuild once mancy-repl
cp out/cljs_mancy.js mancy/clojurescript.js
echo "module.exports = { compiler: cljs_mancy.core, goog: goog, cljs: cljs }" >> mancy/clojurescript.js

