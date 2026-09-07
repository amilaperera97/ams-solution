#!/bin/bash

# Update wiremock generator file
sed -i 's|/api/organisation|/api/v1/organisations/org-001|g' generate_mock_data.js
sed -i 's|/api/providers|/api/v1/organisations/org-001/providers|g' generate_mock_data.js
sed -i 's|/api/v1/organisations/org-001/providers/\[\^/\]+/environments|/api/v1/providers/[^/]+/environments|g' generate_mock_data.js
sed -i 's|/api/v1/organisations/org-001/providers/\[\^/\]+|/api/v1/providers/[^/]+|g' generate_mock_data.js
sed -i 's|/api/connections/.*/test|/api/v1/accounts/[^/]+/test-connection|g' generate_mock_data.js

sed -i 's|/api/environments|/api/v1/environments|g' generate_mock_data.js
sed -i 's|/api/accounts|/api/v1/accounts|g' generate_mock_data.js

sed -i 's|/api/certificates|/api/v1/certificates|g' generate_mock_data.js
sed -i 's|/api/dashboard/stats|/api/v1/dashboard/stats|g' generate_mock_data.js

sed -i 's|/api/scans|/api/v1/scans|g' generate_mock_data.js

