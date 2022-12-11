#!/bin/sh

clojure -M:nREPL -m nrepl.cmdline -p 12345 --middleware '[cider.nrepl/cider-middleware]'
