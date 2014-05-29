; JTA filter plugin to save a ttyrec

(ns anbf.ttyrec
  (:require [clojure.java.io :refer [output-stream]])
  (:import [de.mud.jta FilterPlugin]
           [java.nio ByteBuffer ByteOrder])
  (:gen-class
    :name anbf.Ttyrec
    :extends de.mud.jta.Plugin
    :implements [de.mud.jta.FilterPlugin]
    :state state
    :init init))

(defn -getFilterSource [this source]
  (:source @(.state this)))

(defn -setFilterSource [this source]
  (swap! (.state this) assoc :source source))

(defn -read [this b]
  (let [n (.read (:source @(.state this)) b)
        ts (System/currentTimeMillis)
        ts-sec (quot ts 1000)
        ts-usec (* 1000 (- ts (* ts-sec 1000)))
        ttyrec-hdr (doto (ByteBuffer/allocate 12)
                     (.order ByteOrder/LITTLE_ENDIAN))]
    (when (pos? n)
      (doto ttyrec-hdr
        ;(.clear)
        (.putInt ts-sec)
        (.putInt ts-usec)
        (.putInt n))
      (doto (:ttyrec @(.state this))
        (.write (.array ttyrec-hdr) 0 12)
        (.write b 0 n)
        (.flush)))
    n))

(defn -write [this b]
  (.write (:source @(.state this)) b))

(defn -init [bus id]
  [[bus id] (atom {:source nil
                   :ttyrec (output-stream
                             (str (System/currentTimeMillis) ".ttyrec"))})])
