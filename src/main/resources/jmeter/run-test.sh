# 
function download() {
  wget --no-check-certificate "${METERSPHERE_URL}/jmeter/download?testId=${TEST_ID}&resourceId=${RESOURCE_ID}&ratio=${RATIO}&reportId=${REPORT_ID}&resourceIndex=${RESOURCE_INDEX}" -O ${TEST_ID}.zip
  unzip -o ${TEST_ID}.zip -d ${TESTS_DIR}
}

count=3
# download zip
count=3
download
while [ $? -ne 0 ] && [ $count -gt 0 ]; do
  count=$(($count-1))
  sleep 2

  download
done


# check file
if [ -f "${TESTS_DIR}/ms.properties" ]; then
  cat ${TESTS_DIR}/ms.properties >> /opt/jmeter/bin/jmeter.properties
fi

# dns
if [ -f "${TESTS_DIR}/hosts" ]; then
  cat ${TESTS_DIR}/hosts >>/etc/hosts
fi

while [[ $(curl -s -G -d "ratio=${RATIO}" -d "resourceIndex=${RESOURCE_INDEX}" -d "reportId=${REPORT_ID}" ${METERSPHERE_URL}/jmeter/ready) -gt 0 ]]; do
  echo "time syncing..."
  sleep 0.5
done

# run test
jmeter -n -t ${TESTS_DIR}/${TEST_ID}.jmx -l ${REPORT_ID}_${RESOURCE_INDEX}.jtl &
pid=$!

if [ -z ${BACKEND_LISTENER} ] || [ ${BACKEND_LISTENER} = 'false' ]; then
 java -jar generate-report.jar --reportId=${REPORT_ID} --granularity=${GRANULARITY}
fi

echo "waiting jmeter done..."
wait $pid
echo 'jmeter exited.'