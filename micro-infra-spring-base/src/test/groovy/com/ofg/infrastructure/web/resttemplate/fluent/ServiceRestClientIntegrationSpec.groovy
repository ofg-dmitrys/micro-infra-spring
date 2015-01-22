package com.ofg.infrastructure.web.resttemplate.fluent

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.Appender
import com.ofg.config.BasicProfiles
import com.ofg.infrastructure.base.BaseConfiguration
import com.ofg.infrastructure.base.MvcWiremockIntegrationSpec
import com.ofg.infrastructure.base.ServiceDiscoveryStubbingApplicationConfiguration
import com.ofg.infrastructure.discovery.ServiceAlias
import com.ofg.infrastructure.discovery.ServicePath
import com.ofg.infrastructure.discovery.ServiceResolver
import org.junit.ClassRule
import org.junit.contrib.java.lang.system.ProvideSystemProperty
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.SpringApplicationContextLoader
import org.springframework.http.ResponseEntity
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.web.client.ResourceAccessException
import spock.lang.Shared

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse
import static com.ofg.infrastructure.base.dsl.WireMockHttpRequestMapper.wireMockGet

@ActiveProfiles(['stub', BasicProfiles.TEST])
@ContextConfiguration(classes = [BaseConfiguration, ServiceDiscoveryStubbingApplicationConfiguration], loader = SpringApplicationContextLoader)
class ServiceRestClientIntegrationSpec extends MvcWiremockIntegrationSpec {

    private static final String COLLABORATOR_NAME = 'foo-bar'
    private static final String PATH = '/pl/foobar'
    private static final String CONTEXT_SPECIFIC_FOOBAR = 'foobar Poland'

    @Value('${rest.client.readTimeout}')
    int readTimeoutMillis

    @Shared
    @ClassRule
    public ProvideSystemProperty resolverUrlPropertyIsSet = new ProvideSystemProperty('service.resolver.url', 'localhost:2183');

    @Autowired
    ServiceRestClient serviceRestClient

    @Autowired ServiceResolver serviceResolver

    def "should send a request to provided URL with appending host when calling service"() {
        when:
            ResponseEntity<String> result = serviceRestClient
                    .forService(COLLABORATOR_NAME)
                    .get()
                    .onUrl(PATH)
                    .andExecuteFor()
                    .aResponseEntity()
                    .ofType(String)
        then:
            result.body == CONTEXT_SPECIFIC_FOOBAR
    }

    def "should throw an exception when service does not respond"() {
        given:
            stubInteraction(wireMockGet('/delayed'), aResponse()
                    .withFixedDelay(readTimeoutMillis * 2)
            )
        when:
            serviceRestClient.forExternalService()
                    .get()
                    .onUrl("http://localhost:${httpMockServer.port()}/delayed")
                    .andExecuteFor()
                    .anObject().ofType(String)
        then:
            def exception = thrown(ResourceAccessException)
            exception.cause instanceof SocketTimeoutException
            exception.message.contains('Read timed out')
    }

    def 'should log HTTP response using Logback'() {
        given:
            final Appender mockAppender = insertAppender(Mock(Appender.class))
        when:
            ResponseEntity<String> result = serviceRestClient
                    .forService(COLLABORATOR_NAME)
                    .get()
                    .onUrl(PATH)
                    .andExecuteFor()
                    .aResponseEntity()
                    .ofType(String)
        then:
            (1.._) * mockAppender.doAppend({ILoggingEvent e ->
                e.formattedMessage.contains(CONTEXT_SPECIFIC_FOOBAR)
            })
        and:
            result.body == CONTEXT_SPECIFIC_FOOBAR
        cleanup:
            removeAppender(mockAppender)
    }

    Appender insertAppender(Appender appender) {
        root().addAppender(appender);
        return appender
    }

    void removeAppender(Appender appender) {
        root().detachAppender(appender)
    }

    private Logger root() {
        return (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)
    }

    def "should properly construct parameterized external URL"() {
        given:
            ServicePath path = serviceResolver.resolveAlias(new ServiceAlias(COLLABORATOR_NAME))
            URI uri = serviceResolver.getUri(path).get()
        when:
            String result = serviceRestClient
                    .forExternalService()
                    .get()
                    .onUrlFromTemplate(uri.toString() + '/pl/{name}')
                    .withVariables('foobar')
                    .andExecuteFor()
                    .anObject()
                    .ofType(String)
        then:
            result == CONTEXT_SPECIFIC_FOOBAR
    }

    def "should properly construct external URL from template"() {
        given:
            ServicePath path = serviceResolver.resolveAlias(new ServiceAlias(COLLABORATOR_NAME))
            String uri = serviceResolver.getUri(path).get().toString()
        when:
            String result = serviceRestClient
                    .forExternalService()
                    .get()
                    .onUrlFromTemplate('{uri}/pl/{name}')
                    .withVariables(uri, 'foobar')
                    .andExecuteFor()
                    .anObject()
                    .ofType(String)
        then:
            result == CONTEXT_SPECIFIC_FOOBAR
    }

    def "should properly construct external URL from GString"() {
        given:
            URI uri = uriOf(COLLABORATOR_NAME)
            String name = 'foobar'
        when:
            String result = serviceRestClient
                    .forExternalService()
                    .get()
                    .onUrl("${uri.toString()}/pl/$name")
                    .andExecuteFor()
                    .anObject()
                    .ofType(String)
        then:
            result == CONTEXT_SPECIFIC_FOOBAR
    }

    private URI uriOf(String collaboratorAlias) {
        ServicePath path = serviceResolver.resolveAlias(new ServiceAlias(COLLABORATOR_NAME))
        return serviceResolver.getUri(path).get()
    }

}
