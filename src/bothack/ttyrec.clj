(ns bothack.ttyrec
  "JTA filter plugin to save a ttyrec"
  (:require [clojure.java.io :refer [output-stream]])
  (:import [de.mud.jta FilterPlugin]
           [java.nio ByteBuffer ByteOrder])
  (:gen-class
    :name bothack.Ttyrec
    :extends de.mud.jta.Plugin
    :implements [de.mud.jta.FilterPlugin]
    :state state
    :init init))

(defn -getFilterSource [this source]
  (:source @(.state this)))

(defn -setFilterSource [this source]
  (swap! (.state this) assoc :source source))

(defn -read [this b]
  (let [n (.read ^FilterPlugin (:source @(.state ^bothack.Ttyrec this)) b)
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
      (doto ^java.io.OutputStream (:ttyrec @(.state ^bothack.Ttyrec this))
        (.write (.array ttyrec-hdr) 0 12)
        (.write b 0 n)
        (.flush)))
    n))

(defn -write [this b]
  (.write ^FilterPlugin (:source @(.state ^bothack.Ttyrec this)) b))

(defn -init [bus id]
  [[bus id] (atom {:source nil
                   :ttyrec (output-stream
                             (str (System/currentTimeMillis) ".ttyrec"))})])
