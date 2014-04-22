(ns anbf.util)

(defn ctrl [ch]
  "Returns a char representing CTRL+<ch>"
  (char (- (int ch) 96)))

(def esc (str (char 27)))

(def backspace (str (char 8)))
