# AGENTS

## Agent skills

### Issue tracker

Issues live as GitHub issues in hlopes/personal-media-tracker. See `docs/agents/issue-tracker.md`.

### Triage labels

Five canonical labels map 1:1 to the tracker. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context with `CONTEXT.md` and `docs/adr/`. See `docs/agents/domain.md`.


#### Rules and Guidelines

- Avoid using fully qualified classes inside the code, for example use "UUID currentEntryId = null;" instead of "java.util.UUID currentEntryId = null;" and import the required class on the top unless there is a class name conflict or duplication
- The order of the @inject class fields should be JsonWebToken, ApplicationConfig then the services, respositories, mappers and in the end the templates
- Resources classes should never use repositories directly, should use services classes instead
- Alwayt place the private methods in the end of classes files
- If there is a duplicated method being used in different classes create an Util class that cannot be instantiate and create the corresponding static method, replacing the old locations where the method was being invoked