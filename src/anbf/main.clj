(ns anbf.main
  (:require [clojure.tools.logging :as log]
            [anbf.util :refer :all]
            [anbf.jta :refer :all]
            [anbf.delegator :refer :all]
            [anbf.term :refer :all]
            [anbf.bot.nao-menu :as nao-menu])
  (:gen-class))

(defrecord ANBF
  [config
   delegator
   jta
   frame])
;  ...

(defmethod print-method ANBF [anbf w]
  (.write w "<ANBF instance>"))

(defn- load-config [fname]
  (binding [*read-eval* false]
    (read-string (slurp fname))))

(defn new-anbf
  ([]
   (new-anbf "anbf-config.clj"))
  ([fname]
   (let [delegator (atom (new-delegator))
         anbf (ANBF. (atom (load-config fname))
                     delegator
                     (new-telnet-jta delegator)
                     (atom nil))]
     (-> anbf
         (register-handler (reify ConnectionStatusHandler
                             (online [_]
                               (log/info "Connection status: online"))
                             (offline [_]
                               (log/info "Connection status: offline"))))
         (register-handler (reify RedrawHandler
                             (redraw [_ frame]
                               (reset! (:frame anbf) frame)
                               (print-frame frame))))))))

(defn start
  ([]
   (def s (new-anbf)) ; "default" instance for the REPL
   (start s))
  ([anbf]
   (log/info "ANBF instance started")
   (let [config @(:config anbf)]
     (start-jta (:jta anbf) (:host config) (:port config))
     (nao-menu/run-menubot anbf)) ; TODO by config
   anbf))

(defn stop
  ([]
   (stop s))
  ([anbf]
   (stop-jta (:jta anbf))
   (log/info "ANBF instance stopped")
   anbf))

(defn -main [& args] []
  (let [anbf (new-anbf)] ; TODO config fname from args
    (def s anbf)
    (start anbf)))
