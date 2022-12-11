#!/bin/bash

clojure -Sdeps '{:deps {reply/reply {:mvn/version "0.4.4"}}}' -M -m reply.main --attach 7777
