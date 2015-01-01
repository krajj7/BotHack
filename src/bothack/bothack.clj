(ns bothack.bothack
  (:require [clojure.tools.logging :as log]
            [bothack.util :refer :all]
            [bothack.jta :refer :all]
            [bothack.action :refer :all]
            [bothack.actions :refer :all]
            [bothack.delegator :refer :all]
            [bothack.dungeon :refer :all]
            [bothack.item :refer :all]
            [bothack.itemid :refer :all]
            [bothack.term :refer :all]
            [bothack.game :refer :all]
            [bothack.handlers :refer :all]
            [bothack.pathing :refer :all]
            [bothack.position :refer :all]
            [bothack.scraper :refer :all]
            [bothack.sokoban :refer :all]
            [bothack.tracker :refer :all]))

(defrecord BotHack [config delegator jta scraper game]
  bothack.bot.IBotHack
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

(defmethod print-method BotHack [bh w]
  (.write w "<BotHack instance>"))

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
  [bh bot-ns]
  (require bot-ns)
  (if-let [bot-init-fn (ns-resolve bot-ns 'init)]
    (bot-init-fn bh)
    (throw (ClassNotFoundException. (str "Failed to resolve init in bot "
                                         bot-ns)))))

(defn- start-java-bot
  "Loads a Java bot class and runs its constructor that accepts bothack.bot.IBotHack as the only parameter."
  [bh bot-class]
  (.newInstance (.getConstructor (resolve bot-class)
                                 (into-array [bothack.bot.IBotHack]))
                (into-array [bh])))

(defn- start-bot [{:keys [config] :as bh}]
  (if-let [bot (config-get config :bot nil)]
    (start-clj-bot bh (symbol bot))
    (if-let [javabot (config-get config :javabot nil)]
      (start-java-bot bh (symbol javabot))
      (throw (IllegalStateException.
               "Missing :bot or :javabot in configuration.")))))

(defn- start-menubot
  "The menubot is responsible for starting the game and letting the delegator know about it by calling 'started' on it when done.  If there is no menubot configured, the game is presumed to be started directly."
  [bh]
  (when-let [menubot-ns (config-get (:config bh) :menubot nil)]
    (start-clj-bot bh (symbol menubot-ns))
    true))

(defn- actions-handler [{:keys [game] :as bh}]
  (let [action-handlers (atom #{})]
    (reify ActionChosenHandler
      (action-chosen [_ action]
        (doseq [h @action-handlers]
          (deregister-handler bh h))
        (reset! action-handlers #{})
        (when-let [h (handler action bh)]
          (register-handler bh priority-top h)
          (swap! action-handlers conj h))
        (doseq [[p handler] (:handlers action)]
          (when-let [h (if (fn? handler)
                         (handler bh)
                         handler)]
            (register-handler bh p h)
            (swap! action-handlers conj h)))
        (swap! game #(assoc % :last-position (position (:player %))
                              :last-action* action
                              :last-state (dissoc % :last-state)))
        (if-not (#{:call :name :discoveries :inventory :look :farlook}
                         (typekw action))
          (swap! game #(assoc % :last-path (get action :path (:last-path %))
                                :last-action action)))))))

(defn pause [bh]
  (send (:delegator bh) set-inhibition true)
  (log/info "pausing")
  bh)

(defn start [{:keys [config delegator] :as bh}]
  (log/info "BotHack instance started")
  (if-not (start-menubot bh)
    (send delegator started))
  (await delegator)
  (start-jta (:jta bh)
             (config-get config :host "localhost")
             (config-get config :port 23))
  bh)

(defn stop [bh]
  (stop-jta (:jta bh))
  (dosync (ref-set (:scraper bh) nil))
  (log/info "BotHack instance stopped")
  bh)

(defn unpause [bh]
  (log/info "unpaused")
  (dosync
    (ref-set (:scraper bh) nil)
    (-> (:delegator bh)
        (send set-inhibition false)
        (send write (str esc esc))))
  bh)

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
    SeducedHandler
    (seduced-puton [_ _] false)
    (seduced-remove [_ _] false)
    StopEatingHandler
    (stop-eating [_ _] true)
    WizmodeEnhanceHandler
    (enhance-without-practice [_ _] false)
    DumpCoreHandler
    (dump-core [_ _] "q")
    DoTeleportHandler
    (do-teleport [_ _] true)
    ReallyAttackHandler
    (really-attack [_ _] false)
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
    WhatNameHandler
    (what-name [_ _] "")))

(defn new-bh
  ([] (new-bh "config/shell-config.edn"))
  ([fname]
   (let [delegator (agent (new-delegator nil)
                          :error-handler
                          #(log/error %2 "delegator caught error"))
         config (load-config fname)
         jta (init-jta config delegator)
         scraper-fn (ref nil)
         game (atom (new-game))
         bh (BotHack. config delegator jta scraper-fn game)
         scraper (scraper-handler scraper-fn delegator)]
     (send delegator set-writer (partial raw-write jta))
     (-> bh
         update-inventory
         update-discoveries
         (register-handler (dec priority-top) (game-handler bh))
         (register-handler (inc priority-bottom)
                           (reify FullFrameHandler
                             (full-frame [_ _]
                               (send delegator #(about-to-choose % @game))
                               (send delegator #(choose-action % @game)))))
         (register-handler priority-top (set-race-role-handler bh))
         (register-handler priority-bottom (actions-handler bh))
         (register-handler priority-top (examine-handler bh))
         (register-handler priority-top (call-id-handler bh))
         (register-handler priority-top (mark-recharge-handler bh))
         (register-handler priority-bottom prompt-escape)
         (register-handler priority-top (soko-handler bh))
         (register-handler priority-top (wish-id-handler bh))
         (register-handler priority-top (itemid-handler bh))
         (register-handler priority-top (reset-exploration bh))
         (register-handler priority-top (death-tracker bh))
         (register-handler priority-bottom
                           (reify FullFrameHandler
                             (full-frame [this _]
                               (if (:start-paused config)
                                 (pause bh))
                               (deregister-handler bh this))))
         (register-handler (reify GameStateHandler
                             (ended [_]
                               (deregister-handler bh scraper))
                             (started [_]
                               (register-handler bh (dec priority-top) scraper)
                               (start-bot bh))))))))
