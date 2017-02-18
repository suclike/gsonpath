package gsonpath.generator.adapter.standard

import com.google.gson.stream.JsonReader
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.CodeBlock
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterizedTypeName
import gsonpath.FlattenJson
import gsonpath.ProcessingException
import gsonpath.generator.adapter.*
import gsonpath.model.*
import java.io.IOException
import javax.lang.model.element.Modifier

/**
 * public T read(JsonReader in) throws IOException {
 */
@Throws(ProcessingException::class)
fun createReadMethod(baseElement: ClassName,
                     concreteElement: ClassName,
                     mandatoryInfoMap: Map<String, MandatoryFieldInfo>,
                     rootElements: GsonObject): MethodSpec {

    // Create a flat list of the variables and ensure they are ordered by their original field index within the POJO
    val flattenedFields = GsonObjectTreeFactory().getFlattenedFieldsFromGsonObject(rootElements)

    val readMethod = MethodSpec.methodBuilder("read")
            .addAnnotation(Override::class.java)
            .addModifiers(Modifier.PUBLIC)
            .returns(baseElement)
            .addParameter(JsonReader::class.java, "in")
            .addException(IOException::class.java)

    val codeBlock = CodeBlock.builder()
    val requiresConstructorInjection = baseElement != concreteElement

    // Before any fields are inspected, we verify if the json contains any content.
    addValidValueCheck(codeBlock, true)

    // Create the class returned by the 'read' method, or define all variables required at the beginning.
    if (!requiresConstructorInjection) {
        codeBlock.addStatement("\$T result = new \$T()", concreteElement, concreteElement)

    } else {
        for (gsonField in flattenedFields) {
            // Don't initialise primitives, we rely on validation to throw an exception if the value does not exist.
            val typeName = gsonField.fieldInfo.typeName
            val defaultValue = createDefaultVariableValueForTypeName(typeName)

            codeBlock.addStatement("$typeName ${gsonField.variableName} = $defaultValue")
        }
    }

    // If we have any mandatory fields, we need to keep track of what has been assigned.
    if (mandatoryInfoMap.isNotEmpty()) {
        codeBlock.addStatement("boolean[] mandatoryFieldsCheckList = new boolean[MANDATORY_FIELDS_SIZE]")
    }

    codeBlock.addNewLine()

    // Add the read code recursively for every single defined element
    addReadCodeForElements(codeBlock, rootElements, requiresConstructorInjection, mandatoryInfoMap)

    // Validate the mandatory fields (if any exist)
    addMandatoryValuesCheck(codeBlock, mandatoryInfoMap, concreteElement)

    if (!requiresConstructorInjection) {
        // If the class was already defined, return it now.
        codeBlock.addStatement("return result")

    } else {
        // Create the class using the constructor.
        val returnCodeBlock = CodeBlock.builder()
                .addWithNewLine("return new \$T(", concreteElement)
                .indent()

        for (i in flattenedFields.indices) {
            returnCodeBlock.add(flattenedFields[i].variableName)

            if (i < flattenedFields.size - 1) {
                returnCodeBlock.add(",")
            }

            returnCodeBlock.addNewLine()
        }

        codeBlock.add(returnCodeBlock.unindent()
                .addStatement(")")
                .build())
    }

    return readMethod.addCode(codeBlock.build())
            .build()
}

/**
 * Adds the read code for the current level of json mapping.
 * This is a recursive function.
 */
@Throws(ProcessingException::class)
private fun addReadCodeForElements(codeBlock: CodeBlock.Builder,
                                   jsonMapping: GsonObject,
                                   requiresConstructorInjection: Boolean,
                                   mandatoryInfoMap: Map<String, MandatoryFieldInfo>,
                                   recursionCount: Int = 0): Int {

    val jsonMappingSize = jsonMapping.size()
    if (jsonMappingSize == 0) {
        return recursionCount
    }

    if (jsonMappingSize == 1) {
        val value = jsonMapping[jsonMapping.keySet().iterator().next()]

        if (value is GsonField && value.fieldInfo.isDirectAccess) {
            writeGsonFieldReader(value, codeBlock, requiresConstructorInjection, mandatoryInfoMap[value.fieldInfo.fieldName])
            return recursionCount + 1
        }
    }

    val counterVariableName = "jsonFieldCounter" + recursionCount
    var newRecursionCount = recursionCount + 1

    codeBlock.addStatement("int $counterVariableName = 0")
            .addStatement("in.beginObject()")
            .addNewLine()
            .beginControlFlow("while (in.hasNext())")

            //
            // Since all the required fields have been mapped, we can avoid calling 'nextName'.
            // This ends up yielding performance improvements on large datasets depending on
            // the ordering of the fields within the JSON.
            //
            .beginControlFlow("if ($counterVariableName == $jsonMappingSize)")
            .addStatement("in.skipValue()")
            .addStatement("continue")
            .endControlFlow() // if
            .addNewLine()

            .beginControlFlow("switch (in.nextName())")

    for (key in jsonMapping.keySet()) {
        codeBlock.add("""case "$key":""")
                .addNewLine()
                .indent()

                // Increment the counter to ensure we track how many fields we have mapped.
                .addStatement("$counterVariableName++")

        val value = jsonMapping[key]
        if (value is GsonField) {
            writeGsonFieldReader(value, codeBlock, requiresConstructorInjection, mandatoryInfoMap[value.fieldInfo.fieldName])

        } else if (value is GsonObject) {
            codeBlock.addNewLine()
            addValidValueCheck(codeBlock, false)

            newRecursionCount = addReadCodeForElements(codeBlock, value, requiresConstructorInjection,
                    mandatoryInfoMap, newRecursionCount)
        }

        codeBlock.addStatement("break")
                .addNewLine()
                .unindent()
    }

    codeBlock.addWithNewLine("default:")
            .indent()
            .addStatement("in.skipValue()")
            .addStatement("break")
            .unindent()

            .endControlFlow() // switch
            .endControlFlow() // while
            .addNewLine()

            .addStatement("in.endObject()")

    return newRecursionCount
}

@Throws(ProcessingException::class)
private fun writeGsonFieldReader(gsonField: GsonField,
                                 codeBlock: CodeBlock.Builder,
                                 requiresConstructorInjection: Boolean,
                                 mandatoryFieldInfo: MandatoryFieldInfo?) {

    val fieldInfo = gsonField.fieldInfo

    // Make sure the field's annotations don't have any problems.
    validateFieldAnnotations(fieldInfo)

    // If the field type is primitive, ensure that it is a supported primitive.
    val fieldTypeName = fieldInfo.typeName
    if (fieldTypeName.isPrimitive && !GSON_SUPPORTED_PRIMITIVE.contains(fieldTypeName)) {
        throw ProcessingException("Unsupported primitive type found. Only boolean, int, double and long can be used.", fieldInfo.element)
    }

    // Add a new line to improve readability for the multi-lined mapping.
    codeBlock.addNewLine()

    val result =
            if (GSON_SUPPORTED_CLASSES.contains(fieldTypeName.box()))
                writeGsonFieldReaderSupported(codeBlock, gsonField, requiresConstructorInjection)
            else
                writeGsonFieldReaderUnsupported(codeBlock, gsonField, requiresConstructorInjection)


    if (result.checkIfNull) {
        codeBlock.beginControlFlow("if (${result.variableName} != null)")

        val assignmentBlock: String
        if (!requiresConstructorInjection) {
            assignmentBlock = "result." + fieldInfo.fieldName
        } else {
            assignmentBlock = gsonField.variableName
        }

        codeBlock.addStatement("$assignmentBlock = ${result.variableName}${if (result.callToString) ".toString()" else ""}")

        // When a field has been assigned, if it is a mandatory value, we note this down.
        if (mandatoryFieldInfo != null) {
            codeBlock.addStatement("mandatoryFieldsCheckList[${mandatoryFieldInfo.indexVariableName}] = true")
                    .addNewLine()
        }

        if (gsonField.isRequired) {
            codeBlock.nextControlFlow("else")
                    .addEscapedStatement("""throw new gsonpath.JsonFieldMissingException("Mandatory JSON element '${gsonField.jsonPath}' was null for class '${fieldInfo.parentClassName}'")""")
        }

        codeBlock.endControlFlow() // if
    }
}

private data class FieldReaderResult(val variableName: String, val checkIfNull: Boolean, val callToString: Boolean = false)

private fun getVariableName(gsonField: GsonField, requiresConstructorInjection: Boolean): String {
    return if (gsonField.isRequired && requiresConstructorInjection)
        "${gsonField.variableName}_safe"
    else
        gsonField.variableName
}

private fun isCheckIfNullApplicable(gsonField: GsonField, requiresConstructorInjection: Boolean): Boolean {
    return !requiresConstructorInjection || gsonField.isRequired
}

/**
 * Writes the Java code for field reading that is supported by Gson.
 */
private fun writeGsonFieldReaderSupported(codeBlock: CodeBlock.Builder, gsonField: GsonField, requiresConstructorInjection: Boolean): FieldReaderResult {
    val fieldInfo = gsonField.fieldInfo

    // Special handling for strings.
    if (fieldInfo.typeName == CLASS_NAME_STRING) {
        val annotation = fieldInfo.getAnnotation(FlattenJson::class.java)
        if (annotation != null) {
            val variableName =
                    if (requiresConstructorInjection)
                        "${gsonField.variableName}_safe"
                    else
                        gsonField.variableName

            codeBlock.addStatement("\$T $variableName = mGson.getAdapter(\$T.class).read(in)",
                    CLASS_NAME_JSON_ELEMENT,
                    CLASS_NAME_JSON_ELEMENT)

            // FlattenJson is a special case. We always need to ensure that the JsonObject is not null.
            return FieldReaderResult(variableName, checkIfNull = true, callToString = true)
        }
    }

    val variableName = getVariableName(gsonField, requiresConstructorInjection)
    val checkIfResultIsNull = isCheckIfNullApplicable(gsonField, requiresConstructorInjection)

    val fieldClassName = fieldInfo.typeName.box() as ClassName
    val variableAssignment = "$variableName = get${fieldClassName.simpleName()}Safely(in)"
    if (checkIfResultIsNull) {
        codeBlock.addStatement("${fieldClassName.simpleName()} $variableAssignment")

    } else {
        codeBlock.addStatement(variableAssignment)
    }

    return FieldReaderResult(variableName, checkIfResultIsNull)
}

/**
 * Writes the Java code for field reading that is not supported by Gson.
 */
private fun writeGsonFieldReaderUnsupported(codeBlock: CodeBlock.Builder, gsonField: GsonField, requiresConstructorInjection: Boolean): FieldReaderResult {
    val fieldTypeName = gsonField.fieldInfo.typeName

    // Handle every other possible class by falling back onto the gson adapter.
    val adapterName =
            if (fieldTypeName is ParameterizedTypeName)
                "new com.google.gson.reflect.TypeToken<$fieldTypeName>(){}" // This is a generic type
            else
                fieldTypeName.toString() + ".class"

    val variableName = getVariableName(gsonField, requiresConstructorInjection)
    val checkIfResultIsNull = isCheckIfNullApplicable(gsonField, requiresConstructorInjection)

    val variableAssignment = "$variableName = mGson.getAdapter($adapterName).read(in)"

    if (checkIfResultIsNull) {
        codeBlock.addStatement("$fieldTypeName $variableAssignment")

    } else {
        codeBlock.addStatement(variableAssignment)
    }

    return FieldReaderResult(variableName, checkIfResultIsNull)
}

/**
 * If there are any mandatory fields, we now check if any values have been missed. If there are, an exception will be raised here.
 */
private fun addMandatoryValuesCheck(codeBlock: CodeBlock.Builder,
                                    mandatoryInfoMap: Map<String, MandatoryFieldInfo>,
                                    concreteElement: ClassName) {

    if (mandatoryInfoMap.isEmpty()) {
        return
    }

    codeBlock.addNewLine()
            .addComment("Mandatory object validation")
            .beginControlFlow("for (int mandatoryFieldIndex = 0; mandatoryFieldIndex < MANDATORY_FIELDS_SIZE; mandatoryFieldIndex++)")

            .addNewLine()
            .addComment("Check if a mandatory value is missing.")
            .beginControlFlow("if (!mandatoryFieldsCheckList[mandatoryFieldIndex])")

            // The code must figure out the correct field name to insert into the error message.
            .addNewLine()
            .addComment("Find the field name of the missing json value.")
            .addStatement("String fieldName = null")
            .beginControlFlow("switch (mandatoryFieldIndex)")

    for ((key, mandatoryFieldInfo) in mandatoryInfoMap) {
        codeBlock.addWithNewLine("case ${mandatoryFieldInfo.indexVariableName}:")
                .indent()
                .addEscapedStatement("""fieldName = "${mandatoryFieldInfo.gsonField.jsonPath}"""")
                .addStatement("break")
                .unindent()
                .addNewLine()
    }

    codeBlock.endControlFlow() // Switch
            .addStatement("""throw new gsonpath.JsonFieldMissingException("Mandatory JSON element '" + fieldName + "' was not found for class '$concreteElement'")""")
            .endControlFlow() // If
            .endControlFlow() // For
}

/**
 * Ensure a Json object exists begin attempting to read it.
 */
private fun addValidValueCheck(codeBlock: CodeBlock.Builder, addReturn: Boolean) {
    codeBlock.addComment("Ensure the object is not null.")
            .beginControlFlow("if (!isValidValue(in))")

            .addStatement(if (addReturn) "return null" else "break")
            .endControlFlow() // if
}