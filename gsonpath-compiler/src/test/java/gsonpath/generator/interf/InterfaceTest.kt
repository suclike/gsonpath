package gsonpath.generator.interf

import com.google.testing.compile.JavaFileObjects
import gsonpath.GsonProcessorImpl
import gsonpath.generator.BaseGeneratorTest
import org.junit.Test

import com.google.common.truth.Truth.assertAbout
import com.google.testing.compile.JavaSourceSubjectFactory.javaSource

class InterfaceTest : BaseGeneratorTest() {
    @Test
    fun testValidInterface() {
        assertGeneratedContent(TestCriteria("generator/interf/valid",

                absoluteSourceNames = listOf(
                        "generator/standard/TestGsonTypeFactory.java"),

                relativeSourceNames = listOf(
                        "TestValidInterface.java"),

                relativeGeneratedNames = listOf(
                        "TestValidInterface_GsonPathModel.java",
                        "TestValidInterface_GsonTypeAdapter.java")
        ))
    }

    @Test
    fun testInvalidInterface_returningVoid() {
        val source = JavaFileObjects.forResource("generator/interf/invalid/TestValidInterface_ReturningVoid.java")

        assertAbout(javaSource()).that(source)
                .processedWith(GsonProcessorImpl())
                .failsToCompile()
                .withErrorContaining("Gson Path interface methods must have a return type")
                .`in`(source)
                .onLine(7)
    }

    @Test
    fun testInvalidInterface_withParameters() {
        val source = JavaFileObjects.forResource("generator/interf/invalid/TestValidInterface_WithParameters.java")

        assertAbout(javaSource()).that(source)
                .processedWith(GsonProcessorImpl())
                .failsToCompile()
                .withErrorContaining("Gson Path interface methods must not have parameters")
                .`in`(source)
                .onLine(7)
    }

    @Test
    fun testUsingPrimitives() {
        assertGeneratedContent(TestCriteria("generator/interf/primitive",

                absoluteSourceNames = listOf(
                        "generator/standard/TestGsonTypeFactory.java"),

                relativeSourceNames = listOf(
                        "TestUsingPrimitives.java"),

                relativeGeneratedNames = listOf(
                        "TestUsingPrimitives_GsonPathModel.java",
                        "TestUsingPrimitives_GsonTypeAdapter.java")
        ))
    }

    @Test
    fun testUsingInheritance() {
        assertGeneratedContent(TestCriteria("generator/interf/inheritance",

                absoluteSourceNames = listOf(
                        "generator/standard/TestGsonTypeFactory.java"),

                relativeSourceNames = listOf(
                        "TestUsingInheritanceBase.java",
                        "TestUsingInheritance.java"),

                relativeGeneratedNames = listOf(
                        "TestUsingInheritance_GsonPathModel.java",
                        "TestUsingInheritance_GsonTypeAdapter.java")
        ))
    }

    @Test
    fun testFlattenJsonWithInterface() {
        assertGeneratedContent(TestCriteria("generator/interf/flatten_json",

                absoluteSourceNames = listOf(
                        "generator/standard/TestGsonTypeFactory.java"),

                relativeSourceNames = listOf(
                        "TestFlattenJsonWithInterface.java"),

                relativeGeneratedNames = listOf(
                        "TestFlattenJsonWithInterface_GsonPathModel.java",
                        "TestFlattenJsonWithInterface_GsonTypeAdapter.java")
        ))
    }
}
