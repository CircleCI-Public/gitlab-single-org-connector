# CircleCI GitLab Connector Demonstration

This project demonstrates the concept of a CircleCI Connector by connecting a
single org on GitLab.com to CircleCI.com

**This software is experimental and for demonstration purposes only. It is
provided as-is and with no warranty or other representation of production
quality.**

## CircleCI configuration

To authenticate as an integration in CircleCI, the connector needs to wield a CircleCI integration
token. This token allows the connector to trigger pipelines with custom configuration on any
associated projects.

Set your integration token in the connector configuration:
```yaml
circleCi:
    apiToken: "my-integration-token"
```

## GitLab configuration

In Gitlab's project UI, go to `Settings > Integrations`, and add a webhook
trigger, using the connector's base URL and the `/hook` route, e.g. 
`https://connector.example.com/hook`.

Add a shared secret (see configuration below), and leave SSL verification
enabled.

Set your auth token, required to download your CircleCI config file in the
configuration:
```yaml
gitlab:
  authToken: "my-auth-token"
```

Gitlab allows you to specify a shared secret which they will send with every
webhook delivery. If you add the following to your configuration it will
require every webhook to include a `X-Gitlab-Token: my-shared-secret` header.

```yaml
gitlab:
  sharedSecretForHooks: "my-shared-secret"
  authToken: "my-auth-token"
```

If that configuration item is not set, no validation will be done. If it _is_
set then it is required to be present and any hook that does not set the
header will be given a `403 Forbidden` response.

## Mapping GitLab repositories

The connector operates by taking advantage of the existing GitHub to CircleCI
integration. Thus it requires you to setup a GitHub project and configure the
GitLab to GitHub mapping in the configuration.

For example, for the GitLab project with ID 1000, map it to the `myorg/myrepo`
GitHub repository:

```yaml
domainMapping:
  repositories:
    1000: gh/myorg/myrepo
```

To be able to checkout code from the repo, an SSH that provides access to it
has to be manually installed in CircleCI, and this key's fingerprint registered
with the connector:

```yaml
domainMapping:
  sshFingerprints:
    1000: "aa:aa:aa:aa:aa:aa:aa:aa:aa:aa:aa:aa:aa:aa:aa:aa"
```

This allows you to use the `git-checkout` command in your CircleCI config YAML:
```yaml
version: 2.1
jobs:
  build:
    steps:
      - git-checkout # check out the code in the project directory
```

## Logging

By default, this service will log to standard output. To configure it further,
please refer to the [Dropwizard logging configuration
documentation](https://www.dropwizard.io/0.8.0/docs/manual/core.html#logging).

## StatsD Metrics

By default this service exposes metrics at `/metrics` on port 8081. If you
need the service to emit statsd metrics you can add some or all of the
following to the config file for the service.

```yaml
statsd:
  host: statsd.example.com
  port: 8125
  refreshPeriodSeconds: 10
```

The only mandatory key is `host`. The others are set to their default values
above.
