(ns anbf.main
  (:require [clojure.tools.logging :as log]
            [anbf.util :refer :all]
            [anbf.jta :refer :all]
            [anbf.delegator :refer :all]
            [anbf.term :refer :all]
            [anbf.scraper :refer :all])
  (:gen-class))

(defrecord ANBF
  [config
   delegator
   jta
   scraper
   frame])
;  ...

(defmethod print-method ANBF [anbf w]
  (.write w "<ANBF instance>"))

(defn- load-config [fname]
  (try
    (binding [*read-eval* false]
      (read-string (slurp fname)))
    (catch Exception e
      (throw (IllegalStateException.
               (format "Failed to load configuration from %s: %s" fname
                       (.getMessage e)))))))

(defn- init-jta [delegator config]
  (case (config-get-direct config :interface)
    :telnet (new-telnet-jta delegator)
    :shell (new-shell-jta delegator (config-get-direct config :nh-command))
    (throw (IllegalArgumentException. "Invalid interface configuration"))))

(defn- start-bot
  "Dynamically loads the given namespace of a bot and runs its init function"
  [anbf bot-ns]
  (require bot-ns)
  (if-let [bot-init-fn (ns-resolve bot-ns 'init)]
    (bot-init-fn anbf)
    (throw (ClassNotFoundException. (str "Failed to resolve init in bot "
                                         bot-ns)))))

(defn- start-menubot
  "The menubot is responsible for starting the game and letting the delegator know about it by calling 'started' on it when done.  If there is no menubot configured, the game is presumed to be started directly."
  [anbf]
  (if-let [menubot-ns (config-get anbf :menubot nil)]
    (or (start-bot anbf (symbol menubot-ns)) true)
    (log/info "No menubot configured")))

(defn new-anbf
  ([]
   (new-anbf "anbf-shell-config.clj"))
  ([fname]
   (let [delegator (agent (new-delegator nil))
         config (load-config fname)
         bot (symbol (config-get-direct config :bot))
         jta (init-jta delegator config)
         initial-scraper (ref nil)
         initial-frame (atom nil)
         anbf (ANBF. config delegator jta initial-scraper initial-frame)
         scraper (scraper-handler anbf)]
     (send-off delegator set-writer (partial raw-write jta))
     (-> anbf
         (register-handler (reify ConnectionStatusHandler
                             (online [_]
                               (log/info "Connection status: online"))
                             (offline [_]
                               (log/info "Connection status: offline"))))
         (register-handler (reify RedrawHandler
                             (redraw [_ frame]
                               (reset! (:frame anbf) frame)
                               (println frame))))
         (register-handler (reify ToplineMessageHandler
                             (message [_ text]
                               (log/info "Topline message: " text))))
         (register-handler (reify GameStateHandler
                             (ended [_]
                               (log/info "Game ended")
                               (dosync (ref-set (:scraper anbf) nil))
                               (deregister-handler anbf scraper))
                             (started [_]
                               (log/info "Game started")
                               (register-handler anbf scraper)
                               (start-bot anbf bot))))))))

(defn start [anbf]
  (def s anbf) ; "default" instance for the REPL
  (log/info "ANBF instance started")
  (if-not (start-menubot anbf)
    (started @(:delegator anbf)))
  (start-jta (:jta anbf)
             (config-get anbf :host "localhost")
             (config-get anbf :port 23))
  anbf)

(defn stop
  ([]
   (stop s))
  ([anbf]
   (stop-jta (:jta anbf))
   (log/info "ANBF instance stopped")
   anbf))

(defn pause [anbf]
  (send-off (:delegator anbf) set-inhibition true)
  anbf)

(defn unpause [anbf]
  (dosync
    (ref-set (:scraper anbf) nil)
    (-> (:delegator anbf)
        (send-off set-inhibition false)
        (send-off redraw @(:frame anbf))))
  anbf)

(defn- w
  "Helper write function for the REPL"
  [ch]
  (raw-write (:jta s) ch))

(defn- p []
  (pause s))

(defn- u []
  (unpause s))

(defn -main [& args] []
  (->> (take 1 args) (apply new-anbf) start))
