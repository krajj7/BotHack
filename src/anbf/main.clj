(ns anbf.main
  (:require [clojure.tools.logging :as log]
            [anbf.util :refer :all]
            [anbf.jta :refer :all]
            [anbf.delegator :refer :all]
            [anbf.term :refer :all])
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

(defn init-jta [delegator config]
  (case (:interface config)
    :telnet (new-telnet-jta delegator)
    :shell (new-shell-jta delegator (:nh-command config))
    (throw (IllegalArgumentException. "Invalid interface configuration"))))

(defn new-anbf
  ([]
   (new-anbf "anbf-config.clj"))
  ([fname]
   (let [delegator (atom (new-delegator))
         config (load-config fname)
         anbf (ANBF. config
                     delegator
                     (init-jta delegator config)
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

(defn- start-bot [anbf bot-ns]
  "Dynamically loads the given namespace of a bot and runs its start function"
  (require bot-ns)
  (if-let [bot-start (ns-resolve bot-ns 'start)]
    (bot-start anbf)  
    (throw (ClassNotFoundException. (format "Failed to resolve start in bot %s"
                                            bot-ns)))))

(defn- start-menubot [anbf]
  (if-let [menubot-ns (-> anbf :config :menubot)]
    (start-bot anbf (symbol menubot-ns))
    (log/info "No menubot configured")))

(defn start
  ([]
   (def s (new-anbf)) ; "default" instance for the REPL
   (start s))
  ([{:keys [config] :as anbf}]
   (log/info "ANBF instance started")
   (start-menubot anbf)
   (start-jta (:jta anbf) (:host config) (:port config))
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
