# Definition of Done

A story is done only when **all** of these are true:

- [ ] Every acceptance criterion has at least one automated test.
- [ ] Tests were written before or alongside the code, and all of them pass locally.
- [ ] The change was made on its own branch and merged through a pull request.
- [ ] CI is green on the pull request.
- [ ] New endpoints appear in Swagger with request and response examples.
- [ ] No secrets, API keys or passwords are committed.
- [ ] Database changes are Flyway migrations; existing migrations are never edited.
- [ ] README or docs are updated if setup or behaviour changed.
- [ ] The devlog has an entry: what I built, what broke, what I learned.
