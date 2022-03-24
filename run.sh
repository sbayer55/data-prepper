./gradlew \
    --exclude-task :e2e-test:trace:checkstyleIntegrationTest \
    --exclude-task :e2e-test:trace:spotlessApply \
    --exclude-task :e2e-test:trace:spotlessJavaCheck \
    :e2e-test:trace:serviceMapEndToEndTest
