$startovs = <<SCRIPT
    sudo /usr/local/share/openvswitch/scripts/ovs-ctl --system-id=random start
    sudo ovs-vsctl --version
SCRIPT

Vagrant::DEFAULT_SERVER_URL.replace('https://vagrantcloud.com')

Vagrant.configure("2") do |config|
  config.vm.box = "floodlight/mininet"
  config.vm.box_version = "0.0.1"

  config.vm.hostname = "floodlight"
  config.vm.network :private_network, type: "dhcp"

  config.vm.synced_folder "example/", "/home/vagrant/example/"

  config.vm.provision "shell", inline: $startovs
end