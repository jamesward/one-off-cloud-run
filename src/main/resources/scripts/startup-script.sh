#!/bin/bash

if [[ ! -d "/etc/custom_services" ]]; then
  mkdir /etc/custom_services
fi

export POWER_OFF_WHEN_DONE=$(curl -sf http://metadata.google.internal/computeMetadata/v1/instance/attributes/POWER_OFF_WHEN_DONE -H "Metadata-Flavor: Google")

if [[ ! -z "$POWER_OFF_WHEN_DONE" ]]; then

cat <<'EOF' > /etc/custom_services/watcher.sh
#!/bin/bash

while true; do
  uptime=$(cat /proc/uptime | awk '{printf "%0.f", $1}')
  num_all_docker_ps=$(docker ps | tail -n +2 | wc -l)
  num_user_docker_ps=$(docker ps | grep -v stackdriver-logging-agent | tail -n +2 | wc -l)
  if ((num_all_docker_ps == "1")) && ((num_user_docker_ps == "0")) && ((uptime > 60)); then
    echo "no docker processes running, so shutting down"
    poweroff
  fi
  sleep 1
done
EOF

chmod +x /etc/custom_services/watcher.sh

cat <<'EOF' > /etc/systemd/system/watcher.service
[Unit]
Description=watcher service

[Service]
Type=simple
ExecStart=/etc/custom_services/watcher.sh

[Install]
WantedBy=multi-user.target
EOF

systemctl start watcher

fi

export INSTANCE_CONNECTION_NAME=$(curl -sf http://metadata.google.internal/computeMetadata/v1/instance/attributes/INSTANCE_CONNECTION_NAME -H "Metadata-Flavor: Google")

if [[ ! -z "$INSTANCE_CONNECTION_NAME" ]]; then

wget https://dl.google.com/cloudsql/cloud_sql_proxy.linux.amd64 -O /etc/custom_services/cloud_sql_proxy
chmod +x /etc/custom_services/cloud_sql_proxy

cat <<EOF > /etc/systemd/system/cloud_sql_proxy.service
[Unit]
Description=cloud_sql_proxy service

[Service]
Type=simple
ExecStart=/etc/custom_services/cloud_sql_proxy -dir=/mnt/stateful_partition/cloudsql -instances=$INSTANCE_CONNECTION_NAME

[Install]
WantedBy=multi-user.target
EOF

systemctl start cloud_sql_proxy

fi
