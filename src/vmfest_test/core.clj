(ns vmfest-test.core
  (:require [pallet.script.lib :as lib]
            [pallet.stevedore :refer [checked-script]]
            [vmfest-test.daemontools :as daemontools])
  (:use [clojure.pprint :only [pprint]]
        
        [pallet.api :only [lift converge group-nodes plan-fn]]
        [pallet.configure :only [compute-service]]
        [pallet.action :only [defaction implement-action]]
        [pallet.actions :only [package package-manager remote-file remote-directory directory]]
        [pallet.core :only [group-spec node-spec server-spec]]
        [pallet.actions :only [exec-script exec-checked-script]]
        [pallet.compute :only [images]]
        
        [pallet.crate.automated-admin-user :only [automated-admin-user]]))

(def vmfest (compute-service "vmfest"))

(use '[pallet.compute.vmfest :only [add-image]])
#_ (add-image vmfest "https://s3.amazonaws.com/vmfest-images/ubuntu-12.04.vdi.gz")

(defaction squid-deb-proxy-client-package
  {:execution :aggregated
   :always-after #{:package-manager :package-source}
   :always-before package}
  [])

(implement-action squid-deb-proxy-client-package :direct
                  "Install squid-deb-proxy-client package"
                  {:action-type :script :location :target}
                  [session & args]
                  [[{:language :bash}
                    (checked-script
                     "Install squid-deb-proxy-client package"
                     (~lib/install-package "squid-deb-proxy-client"))]
                   session])


(def base-server
  (server-spec
   :node-spec {:image {:image-id :ubuntu-12.04}}
   :phases {:bootstrap (plan-fn (automated-admin-user)
                                (package-manager :update))
            :configure (plan-fn (squid-deb-proxy-client-package)
                                (package "openjdk-6-jdk"))}))

;; sudo tail -F /var/log/squid-deb-proxy/access.log
(def squid-deb-proxy-group
  (group-spec "squid-deb-proxy"
              :extends [base-server]
              :phases {:configure (plan-fn
                                   (exec-script "sudo apt-get update --fix-missing")
                                   (package "squid-deb-proxy"))}))

#_ (converge {squid-deb-proxy-group 1} :compute vmfest)



(def lein-package
  (plan-fn (remote-file "/usr/bin/lein"
                         :url "https://raw.github.com/technomancy/leiningen/stable/bin/lein"
                         :mode 755)
           (exec-checked-script "Run leiningen first time to bootstrap"
                                "export LEIN_ROOT=1"
                                "lein version")))

(def zk-server
  (server-spec
   :extends [base-server]
   :phases {:configure (plan-fn (package "zookeeper")
                                (exec-checked-script
                                 "Start Zookeeper Server"
                                 "/usr/share/zookeeper/bin/zkServer.sh start"))}))



(def nimbus-server
  (server-spec
   :extends [base-server]
   :phases {:configure (plan-fn (package "git")
                                (package "zip")
                                ;;(lein-package)
                                ;;(package "maven2")

                                (directory "storm-local")

                                #_ (remote-file "/storm.zip" :local-file "/Users/jackson/d/binary/storm/storm-0.9.0-wip21.zip")

                                (remote-directory "storm-release"
                                                  :local-file "/Users/jackson/d/binary/storm/storm-0.9.0-wip21.zip"
                                                  :unpack :unzip)

                                ;;(exec-script "ln -s storm-release/*" "storm")
                                ;;(daemontools/supervise "nimbus" :run-script "#!/bin/bash\n$HOME/storm/bin/storm nimbus\n")
                                ;;(daemontools/supervise "stormui" :run-script "#!/bin/bash\n$HOME/storm/bin/storm ui\n")
                                )}))

(def nimbus-zk-group
  (group-spec "storm-nimbus-zk"
              :extends [nimbus-server zk-server daemontools/server-spec]))

(def test-group
  (group-spec "test"
              :extends [base-server daemontools/server-spec]
              :phases {:configure (plan-fn (daemontools/supervise "test"
                                                                   :run-script "#!/bin/bash\necho hi"))}))



#_ (converge {nimbus-zk-group 0} :compute vmfest)
#_ (converge {nimbus-zk-group 1} :compute vmfest)

#_ (converge {test-group 1} :compute vmfest)



#_ (lift [test-group] :compute vmfest)
#_ (lift [nimbus-zk-group] :compute vmfest)



#_ (lift ubuntu-group :compute vmfest :phase (plan-fn (package-manager :update)))
#_ (lift ubuntu-group :compute vmfest :phase (plan-fn (package "zookeeper")))
#_ (lift ubuntu-group :compute vmfest :phase (plan-fn (exec-script "ls /etc/zookeeper/conf")))

#_ (->> (group-nodes vmfest [nimbus-zk-group squid-deb-proxy-group test-group])
        (map :node))









