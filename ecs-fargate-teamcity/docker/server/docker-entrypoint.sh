#!/bin/bash

replace_teamcity_url() {
    echo "Replacing %TEAMCITY_PUBLIC_URL% with ${TEAMCITY_PUBLIC_URL}"
    sed -i "s/%TEAMCITY_PUBLIC_URL%/${TEAMCITY_PUBLIC_URL}/g" /opt/teamcity/conf/server.xml
    echo "Replaced %TEAMCITY_PUBLIC_URL% with ${TEAMCITY_PUBLIC_URL}"
}

if [ -z "${TEAMCITY_PUBLIC_URL}" ]; then
    echo "Error: TEAMCITY_PUBLIC_URL environment variable is not set."
    exit 1
fi

replace_teamcity_url
#
echo "Updating permission for caches..."
chown -R tcuser:tcuser /data/teamcity_server/datadir/system
#ln -s /tmp/cache /data/teamcity_server/datadir/system/caches
echo "Updated permission for caches..."

echo "Starting TeamCity server..."
exec /run-services.sh