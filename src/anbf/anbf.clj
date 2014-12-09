(ns anbf.anbf
  (:require [clojure.tools.logging :as log]
            [anbf.util :refer :all]
            [anbf.jta :refer :all]
            [anbf.action :refer :all]
            [anbf.actions :refer :all]
            [anbf.delegator :refer :all]
            [anbf.dungeon :refer :all]
            [anbf.item :refer :all]
            [anbf.itemid :refer :all]
            [anbf.term :refer :all]
            [anbf.game :refer :all]
            [anbf.handlers :refer :all]
            [anbf.pathing :refer :all]
            [anbf.position :refer :all]
            [anbf.scraper :refer :all]
            [anbf.sokoban :refer :all]
            [anbf.tracker :refer :all]))

(defrecord ANBF [config delegator jta scraper game]
  anbf.bot.IANBF
  (registerHandler [this handler]
    (register-handler this handler))
  (registerHandler [this priority handler]
    (register-handler this priority handler))
  (deregisterHandler [this handler]
    (deregister-handler this handler))
  (replaceHandler [this handler-old handler-new]
    (replace-handler this handler-old handler-new))
  (game [this] @(:game this))
  (player [this] (:player @(:game this)))
  (write [this text] (send (:delegator this) write text) this))

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

(defn- start-clj-bot
  "Dynamically loads the given namespace of a bot and runs its init function"
  [anbf bot-ns]
  (require bot-ns)
  (if-let [bot-init-fn (ns-resolve bot-ns 'init)]
    (bot-init-fn anbf)
    (throw (ClassNotFoundException. (str "Failed to resolve init in bot "
                                         bot-ns)))))

(defn- start-java-bot
  "Loads a Java bot class and runs its constructor that accepts anbf.bot.IANBF as the only parameter."
  [anbf bot-class]
  (.newInstance (.getConstructor (resolve bot-class)
                                 ((into-array [anbf.bot.IANBF])))
                (into-array [anbf])))

(defn- start-bot [{:keys [config] :as anbf}]
  (if-let [bot (config-get config :bot nil)]
    (start-clj-bot anbf (symbol bot))
    (if-let [javabot (config-get config :javabot nil)]
      (start-java-bot anbf (symbol javabot))
      (throw (IllegalStateException.
               "Missing :bot or :javabot in configuration.")))))

(defn- start-menubot
  "The menubot is responsible for starting the game and letting the delegator know about it by calling 'started' on it when done.  If there is no menubot configured, the game is presumed to be started directly."
  [anbf]
  (when-let [menubot-ns (config-get (:config anbf) :menubot nil)]
    (start-clj-bot anbf (symbol menubot-ns))
    true))

(defn- actions-handler [{:keys [game] :as anbf}]
  (let [action-handlers (atom #{})]
    (reify ActionChosenHandler
      (action-chosen [_ action]
        (doseq [h @action-handlers]
          (deregister-handler anbf h))
        (reset! action-handlers #{})
        (when-let [h (handler action anbf)]
          (register-handler anbf priority-top h)
          (swap! action-handlers conj h))
        (doseq [[p handler] (:handlers action)]
          (when-let [h (if (fn? handler)
                         (handler anbf)
                         handler)]
            (register-handler anbf p h)
            (swap! action-handlers conj h)))
        (swap! game #(assoc % :last-position (position (:player %))
                              :last-action* action
                              :last-state (dissoc % :last-state)))
        (if-not (#{:call :name :discoveries :inventory :look :farlook}
                         (typekw action))
          (swap! game #(assoc % :last-path (get action :path (:last-path %))
                                :last-action action)))))))

(defn pause [anbf]
  (send (:delegator anbf) set-inhibition true)
  (log/info "pausing")
  anbf)

(defn start [{:keys [config delegator] :as anbf}]
  (log/info "ANBF instance started")
  (if-not (start-menubot anbf)
    (send delegator started))
  (await delegator)
  (start-jta (:jta anbf)
             (config-get config :host "localhost")
             (config-get config :port 23))
  anbf)

(defn stop [anbf]
  (stop-jta (:jta anbf))
  (dosync (ref-set (:scraper anbf) nil))
  (log/info "ANBF instance stopped")
  anbf)

(defn unpause [anbf]
  (log/info "unpaused")
  (dosync
    (ref-set (:scraper anbf) nil)
    (-> (:delegator anbf)
        (send set-inhibition false)
        (send write (str esc esc))))
  anbf)

(def ^:private prompt-escape "Default responses for unhandled prompts"
  (reify
    EnterGehennomHandler
    (enter-gehennom [_ _] true)
    StillClimbHandler
    (still-climb [_ _] true)
    WhichRingFingerHandler
    (which-finger [_ _] \l)
    PayDamageHandler
    (pay-damage [_ _] true)
    LiftBurdenHandler
    (lift-burden [_ _ _] true)
    ForceGodHandler
    (force-god [_ _] true)
    SeducedEquipRemoveHandler
    (seduced-remove [_ _] false)
    SeducedPutOnHandler
    (seduced-puton [_ _] false)
    StopEatingHandler
    (stop-eating [_ _] true)
    WizmodeEnhanceHandler
    (enhance-without-practice [_ _] false)
    DumpCoreHandler
    (dump-core [_ _] "q")
    CreateWhatMonsterHandler
    (create-what-monster [_ _] esc)
    IdentifyWhatHandler
    (identify-what [_ _]
      (log/warn "default handler identifying anything")
      #{","})
    MakeWishHandler
    (make-wish [_ _]
      (log/warn "default handler wishing for nothing")
      "nothing")
    GenocideHandler
    (genocide-class [_ _]
      (log/warn "default handler genociding class none")
      "none")
    (genocide-monster [_ _]
      (log/warn "default handler genociding none")
      "none")
    ; escape the rest by default
    ChargeWhatHandler
    (charge-what [_ _] "")
    VaultGuardHandler
    (who-are-you [_ _] "")
    SellItHandler
    (sell-it [_ _ _] "")
    EatWhatHandler
    (eat-what [_ _] "")
    OfferHandler
    (offer-how-much [_ _] "")
    LockHandler
    (lock-it [_ _] "")
    (unlock-it [_ _] "")
    ForceLockHandler
    (force-lock [_ _] "")
    ApplyItemHandler
    (apply-what [_ _] "")
    TeleportWhereHandler
    (teleport-where [_] "")
    LevelTeleportHandler
    (leveltele [_ _] "")
    DryFountainHandler
    (dry-fountain [_ _] "")
    DieHandler
    (die [_ _] (log/warn "died") "")
    KeepSaveHandler
    (keep-save [_ _] "y")
    CallWhatNameHandler
    (call-what-name [_ _] "")))

(defn new-anbf
  ([] (new-anbf "config/shell-config.edn"))
  ([fname]
   (let [delegator (agent (new-delegator nil)
                          :error-handler
                          #(log/error %2 "delegator caught error"))
         config (load-config fname)
         jta (init-jta config delegator)
         scraper-fn (ref nil)
         game (atom (new-game))
         anbf (ANBF. config delegator jta scraper-fn game)
         scraper (scraper-handler scraper-fn delegator)]
     (send delegator set-writer (partial raw-write jta))
     (-> anbf
         update-inventory
         update-discoveries
         (register-handler (dec priority-top) (game-handler anbf))
         (register-handler (inc priority-bottom)
                           (reify FullFrameHandler
                             (full-frame [_ _]
                               (send delegator #(about-to-choose % @game))
                               (send delegator #(choose-action % @game)))))
         (register-handler priority-top (set-race-role-handler anbf))
         (register-handler priority-bottom (actions-handler anbf))
         (register-handler priority-top (examine-handler anbf))
         (register-handler priority-top (call-id-handler anbf))
         (register-handler priority-top (mark-recharge-handler anbf))
         (register-handler priority-bottom prompt-escape)
         (register-handler priority-top (soko-handler anbf))
         (register-handler priority-top (wish-id-handler anbf))
         (register-handler priority-top (itemid-handler anbf))
         (register-handler priority-top (reset-exploration anbf))
         (register-handler priority-top (death-tracker anbf))
         (register-handler priority-bottom
                           (reify FullFrameHandler
                             (full-frame [this _]
                               (if (:start-paused config)
                                 (pause anbf))
                               (deregister-handler anbf this))))
         (register-handler (reify GameStateHandler
                             (ended [_]
                               (deregister-handler anbf scraper))
                             (started [_]
                               (register-handler anbf (dec priority-top)
                                                 scraper)
                               (start-bot anbf))))))))
