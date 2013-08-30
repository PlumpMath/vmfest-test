(ns vmfest-test.daemontools
  (:require [pallet.core]
            [pallet.script.lib :as lib])
  (:use [clojure.pprint :only [pprint]]

        [pallet.crate :only [defplan]]
        [pallet.api :only [lift converge group-nodes plan-fn]]        
        [pallet.action :only [defaction implement-action]]
        [pallet.actions :only [package remote-file remote-directory directory directories]]
        [pallet.actions :only [exec-script exec-checked-script]]
        [pallet.compute :only [images]]))

(defplan supervise
  [name & {:keys [run-script]}]
  (let [runtime-dir (str "/service/" name)
        install-dir (str "/service/." name)
        log-dir (str install-dir "/log")]
    (directory runtime-dir :action :delete)
    (directories [install-dir log-dir])
    (remote-file (str install-dir "/run") :mode 755 :content run-script)
    (remote-file (str log-dir "/run") :mode 755 :content "#!/bin/sh\nexec multilog t ./\n")
    (exec-script ("mv" ~install-dir ~runtime-dir))))


(def server-spec
  (pallet.core/server-spec
   :phases {:bootstrap (plan-fn
                        (package "daemontools")
                        (directory "/service")
                        (exec-script "nohup svscan /service/ > /var/log/svscan-service.log &"))}))



