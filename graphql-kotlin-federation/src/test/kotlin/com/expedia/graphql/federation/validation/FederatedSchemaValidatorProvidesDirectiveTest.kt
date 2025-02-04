package com.expedia.graphql.federation.validation

import com.expedia.graphql.federation.FederatedSchemaGenerator
import com.expedia.graphql.federation.FederatedSchemaGeneratorConfig
import com.expedia.graphql.federation.FederatedSchemaGeneratorHooks
import com.expedia.graphql.federation.FederatedSchemaValidator
import com.expedia.graphql.federation.InvalidFederatedSchema
import com.expedia.graphql.federation.directives.ExtendsDirective
import com.expedia.graphql.federation.directives.ExternalDirective
import com.expedia.graphql.federation.directives.FieldSet
import com.expedia.graphql.federation.directives.KeyDirective
import com.expedia.graphql.federation.directives.ProvidesDirective
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLTypeUtil
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FederatedSchemaValidatorProvidesDirectiveTest {
    private val validator = FederatedSchemaValidator()
    private lateinit var schemaGenerator: FederatedSchemaGenerator

    /**
     * Parameterized JUnit5 test source that provides arguments for validating @provides directive.
     *
     * @sample <testName>, <testClass>, (Optional)<expectedValidationError>
     */
    fun providesDirectiveValidations() = Stream.of(
        Arguments.of("[OK] @provides references valid fields", SimpleProvides::class, null),
        Arguments.of("[ERROR] @provides references local objects", ProvidesLocalType::class, "Invalid federated schema:\n" +
            " - @provides directive is specified on a ProvidesLocalType.providedLocal field references local object"),
        Arguments.of("[ERROR] @provides references local field on @extend object", ProvidesLocalField::class, "Invalid federated schema:\n" +
            " - @provides(fields = text) directive on ProvidesLocalField.provided specifies invalid field set - extended type incorrectly references local field=text"),
        Arguments.of("[ERROR] @provides references interface", ProvidesInterface::class, "Invalid federated schema:\n" +
            " - @provides directive is specified on a ProvidesInterface.providedInterface field but it does not return an object type"),
        Arguments.of("[OK] @provides references list of valid objects", ProvidesList::class, null),
        Arguments.of("[ERROR] @provides references field returning list", ProvidesListField::class, "Invalid federated schema:\n" +
            " - @provides(fields = text) directive on ProvidesListField.provided specifies invalid field set - field set references GraphQLList, field=text"),
        Arguments.of("[ERROR] @provides references field returning interface", ProvidesInterfaceField::class, "Invalid federated schema:\n" +
            " - @provides(fields = data) directive on ProvidesInterfaceField.provided specifies invalid field set - field set references GraphQLInterfaceType, field=data")
    )

    @BeforeEach
    fun beforeTest() {
        val config = FederatedSchemaGeneratorConfig(
            supportedPackages = listOf("com.expedia"),
            hooks = FederatedSchemaGeneratorHooks(mockk())
        )
        schemaGenerator = FederatedSchemaGenerator(config)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("providesDirectiveValidations")
    @Suppress("UnusedPrivateMember")
    fun `validate @provide directive`(testCase: String, targetClass: KClass<*>, expectedError: String?) {
        val validatedType = schemaGenerator.objectType(targetClass) as? GraphQLObjectType
        assertNotNull(validatedType)
        assertEquals(targetClass.simpleName, validatedType.name)

        if (expectedError != null) {
            val exception = assertFailsWith(InvalidFederatedSchema::class) {
                validator.validateGraphQLType(validatedType)
            }
            assertEquals(expectedError, exception.message)
        } else {
            validator.validateGraphQLType(validatedType)
            assertNotNull(validatedType.getDirective("key"))
            val providedField = validatedType.getFieldDefinition("provided")
            assertNotNull(providedField)
            assertNotNull(providedField.getDirective("provides"))
            val providedType = GraphQLTypeUtil.unwrapAll(providedField.type) as? GraphQLObjectType
            assertNotNull(providedType)
            assertNotNull(providedType.getDirective("extends"))
        }
    }

    // ======================= TEST DATA ===========
    /*
    type SimpleProvides @key(fields : "id") {
      description: String!
      id: String!
      provided: ProvidedType! @provides(fields : "text")
    }

    type ProvidedType @extends @key(fields : "id") {
      id: String! @external
      text: String! @external
    }
     */
    @KeyDirective(fields = FieldSet("id"))
    private class SimpleProvides(val id: String, val description: String) {

        @ProvidesDirective(fields = FieldSet("text"))
        fun provided() = ProvidedType(id, "some text")
    }

    @KeyDirective(fields = FieldSet("id"))
    @ExtendsDirective
    private data class ProvidedType(
        @property:ExternalDirective val id: String,
        @property:ExternalDirective val text: String
    )

    /*
    type ProvidesLocalType @key(fields : "id") {
      description: String!
      id: String!
      providedLocal: LocalType! @provides(fields : "text")
    }

    type LocalType {
      id: String!
      text: String!
    }
     */
    @KeyDirective(fields = FieldSet("id"))
    private class ProvidesLocalType(val id: String, val description: String) {

        @ProvidesDirective(fields = FieldSet("text"))
        fun providedLocal() = LocalType(id, "some text")
    }

    private data class LocalType(val id: String, val text: String)

    /*
    type ProvidesLocalField @key(fields : "id") {
      description: String!
      id: String!
      provided: ProvidedWithLocalField! @provides(fields : "text")
    }

    type ProvidedWithLocalField @extends @key(fields : "id") {
      id: String! @external
      text: String!
    }
     */
    @KeyDirective(fields = FieldSet("id"))
    private class ProvidesLocalField(val id: String, val description: String) {

        @ProvidesDirective(fields = FieldSet("text"))
        fun provided() = ProvidedWithLocalField(id, "some text")
    }

    @KeyDirective(fields = FieldSet("id"))
    @ExtendsDirective
    private data class ProvidedWithLocalField(
        @property:ExternalDirective val id: String,
        val text: String
    )

    /*
    type ProvidesInterface @key(fields : "id") {
      description: String!
      id: String!
      providedInterface: ProvidedInterface! @provides(fields : "text")
    }

    interface ProvidedInterface @extends @key(fields : "id") {
      id: String! @external
      text: String! @external
    }
     */
    @KeyDirective(fields = FieldSet("id"))
    private class ProvidesInterface(val id: String, val description: String) {

        @ProvidesDirective(fields = FieldSet("text"))
        fun providedInterface(): ProvidedInterface = throw UnsupportedOperationException("not implemented")
    }

    @KeyDirective(fields = FieldSet("id"))
    @ExtendsDirective
    private interface ProvidedInterface {
        @ExternalDirective
        val id: String
        @ExternalDirective
        val text: String
    }

    /*
    type SimpleProvides @key(fields : "id") {
      description: String!
      id: String!
      provided: [ProvidedType!]! @provides(fields : "text")
    }

    type ProvidedType @extends @key(fields : "id") {
      id: String! @external
      text: String! @external
    }
     */
    @KeyDirective(fields = FieldSet("id"))
    private class ProvidesList(val id: String, val description: String) {

        @ProvidesDirective(fields = FieldSet("text"))
        fun provided(): List<ProvidedType> = listOf(ProvidedType(id, "some text"))
    }

    /*
    type ProvidesListField @key(fields : "id") {
      description: String!
      id: String!
      provided: ProvidedWithList! @provides(fields : "text")
    }

    type ProvidedWithList @extends @key(fields : "id") {
      id: String! @external
      text: [String!]! @external
    }
     */
    @KeyDirective(fields = FieldSet("id"))
    private class ProvidesListField(val id: String, val description: String) {

        @ProvidesDirective(fields = FieldSet("text"))
        fun provided() = ProvidedWithList(id, listOf("some text"))
    }

    @KeyDirective(fields = FieldSet("id"))
    @ExtendsDirective
    private data class ProvidedWithList(
        @property:ExternalDirective val id: String,
        @property:ExternalDirective val text: List<String>
    )

    /*
    type ProvidesInterfaceField @key(fields : "id") {
      description: String!
      id: String!
      provided: ProvidedWithInterface! @provides(fields : "text")
    }

    type ProvidedWithInterface @extends @key(fields : "id") {
      id: String! @external
      data: ProvidedInterface! @external
    }

    interface ProvidedInterface @extends @key(fields : "id") {
      id: String! @external
      text: String! @external
    }
     */
    @KeyDirective(fields = FieldSet("id"))
    private class ProvidesInterfaceField(val id: String, val description: String) {

        @ProvidesDirective(fields = FieldSet("data"))
        fun provided() = ProvidedWithInterface(id)
    }

    @KeyDirective(fields = FieldSet("id"))
    @ExtendsDirective
    private data class ProvidedWithInterface(
        @property:ExternalDirective val id: String,
        @property:ExternalDirective val data: ProvidedInterface = throw UnsupportedOperationException("not implemented")
    )
}
