# Test-only public contract goldens

Exact copies of manabi-frontend/tests/fixtures/json-conformance-v1.json, public/educational-content manifest/packages and development-content/phrases. These fixtures are not editorial content or publication inputs. Maven production resources exclude src/test.

Conformance vector SHA-256: f880c41a8669ea0fc11fa23efe482f8ceb843653142384cce7f7effc7920eae9. Never update golden bytes just to make serialization tests pass.

Run the cross-project Node bridge after Maven tests: node src/test/js/frontend-public-contract.mjs ../manabi-frontend target/public-contract-conformance. It reads existing Web validators without modifying them and rejects technical fixtures in the production validation branch.
