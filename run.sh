./gradlew \
    --exclude-task :e2e-test:trace:checkstyleIntegrationTest \
    --exclude-task :e2e-test:trace:spotlessApply \
    :e2e-test:trace:serviceMapEndToEndTest