package com.chargebee.sdk.java.javanext.builder;

import com.chargebee.GenUtil;
import com.chargebee.openapi.Extension;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Common utility class for deriving method names from API paths.
 * Used by ServiceBuilder, PostResponseBuilder, PostRequestParamsBuilder, etc.
 * to ensure consistent naming across all generated classes.
 */
public final class MethodNameDeriver {

  // Java reserved keywords that cannot be used as method names
  private static final Set<String> JAVA_RESERVED_KEYWORDS = Set.of(
      "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
      "class", "const", "continue", "default", "do", "double", "else", "enum",
      "extends", "final", "finally", "float", "for", "goto", "if", "implements",
      "import", "instanceof", "int", "interface", "long", "native", "new", "package",
      "private", "protected", "public", "return", "short", "static", "strictfp",
      "super", "switch", "synchronized", "this", "throw", "throws", "transient",
      "try", "void", "volatile", "while", "true", "false", "null"
  );

  // Cached set of schema names from the OpenAPI spec
  private static Set<String> schemaNames = null;

  private MethodNameDeriver() {
    // Utility class - prevent instantiation
  }

  /**
   * Initialize the deriver with schema names from the OpenAPI spec.
   * This should be called once before using deriveMethodName.
   */
  public static void initialize(OpenAPI openApi) {
    if (openApi != null && openApi.getComponents() != null && openApi.getComponents().getSchemas() != null) {
      schemaNames = openApi.getComponents().getSchemas().keySet();
    } else {
      schemaNames = Set.of();
    }
  }

  /**
   * Derives a method name from the path and HTTP method.
   * For CRUD operations, uses standard names (create, update, list, retrieve).
   * For other operations, derives name from path segments.
   * Handles Java reserved keywords by suffixing with the resource name.
   *
   * @param path the API path (e.g., "/invoices/{invoice-id}/void")
   * @param httpMethod the HTTP method (GET, POST, etc.)
   * @param operation the OpenAPI operation object
   * @return the derived method name in lowerCamelCase
   */
  public static String deriveMethodName(String path, String httpMethod, Operation operation) {
    // Check if it's a CRUD operation
    String crudMethodName = getCrudMethodName(path, httpMethod, operation);
    if (crudMethodName != null) {
      return crudMethodName;
    }
    // For non-CRUD operations, derive from path
    // Reserved keywords and context disambiguation are handled inside deriveMethodNameFromPathSegments
    return deriveMethodNameFromPathSegments(path, httpMethod);
  }

  /**
   * Determines if the operation is a standard CRUD operation and returns the appropriate method name.
   * Only handles GET and POST HTTP methods.
   */
  private static String getCrudMethodName(String path, String httpMethod, Operation operation) {
    boolean isListOperation = operation != null &&
        operation.getExtensions() != null &&
        operation.getExtensions().get(Extension.IS_OPERATION_LIST) != null &&
        (boolean) operation.getExtensions().get(Extension.IS_OPERATION_LIST);

    String pathWithoutLeadingSlash = path.startsWith("/") ? path.substring(1) : path;
    // Handle batch paths - strip the "batch/" prefix for CRUD detection
    if (pathWithoutLeadingSlash.startsWith("batch/")) {
      pathWithoutLeadingSlash = pathWithoutLeadingSlash.substring(6); // Remove "batch/"
    }
    String[] pathSegments = pathWithoutLeadingSlash.split("/");

    // Check if this is a simple resource path (no extra segments after the resource name)
    boolean hasPathParam = false;
    boolean hasExtraSegmentsAfterResource = false;
    boolean foundResourceSegment = false;

    for (String segment : pathSegments) {
      if (segment.contains("{")) {
        hasPathParam = true;
        foundResourceSegment = true;
      } else if (!foundResourceSegment) {
        foundResourceSegment = true;
      } else {
        hasExtraSegmentsAfterResource = true;
        break;
      }
    }

    boolean isSimpleResourcePath = !hasExtraSegmentsAfterResource;

    switch (httpMethod.toUpperCase()) {
      case "POST":
        if (isSimpleResourcePath && !hasPathParam) {
          return "create";
        } else if (isSimpleResourcePath && hasPathParam) {
          return "update";
        }
        break;
      case "GET":
        if (isListOperation && isSimpleResourcePath && !hasPathParam) {
          return "list";
        } else if (isListOperation && hasExtraSegmentsAfterResource && !hasPathParam) {
          // List operation on sub-resource like /business_entities/transfers -> listTransfers
          String lastSegment = pathSegments[pathSegments.length - 1];
          return "list" + GenUtil.toClazName(lastSegment);
        } else if (hasPathParam && isSimpleResourcePath) {
          return "retrieve";
        }
        break;
    }

    return null; // Not a standard CRUD operation
  }

  /**
   * Derives method name from path segments for non-CRUD operations.
   * Handles cross-resource operations like /invoices/{id}/orders -> ordersForInvoice (GET only)
   * For POST on cross-resource paths, uses "add" prefix to distinguish from GET list operations.
   */
  private static String deriveMethodNameFromPathSegments(String path, String httpMethod) {
    String pathWithoutLeadingSlash = path.startsWith("/") ? path.substring(1) : path;
    String[] pathSegments = pathWithoutLeadingSlash.split("/");

    // Detect cross-resource pattern: /resource1/{id}/resource2
    // e.g., /invoices/{invoice-id}/orders -> ordersForInvoice (GET) or addOrderForInvoice (POST)
    // Only applies when the third segment is a resource (exists in schema)
    if (pathSegments.length == 3) {
      String firstResource = pathSegments[0];
      String secondSegment = pathSegments[1];
      String thirdSegment = pathSegments[2];
      
      // Check if pattern is: resource/{id}/otherResource (cross-resource)
      // The third segment should be a known resource from the schema
      if (secondSegment.contains("{") && !thirdSegment.contains("{") && !thirdSegment.isEmpty()
          && isKnownResource(thirdSegment)) {
        String singularFirstResource = singularize(firstResource);
        
        if ("GET".equalsIgnoreCase(httpMethod)) {
          // GET: list operation -> ordersForInvoice
          String methodName = thirdSegment + "_for_" + singularFirstResource;
          return GenUtil.normalizeToLowerCamelCase(methodName);
        } else if ("POST".equalsIgnoreCase(httpMethod)) {
          // POST: add/create operation -> addOrderForInvoice
          String singularThirdResource = singularize(thirdSegment);
          String methodName = "add_" + singularThirdResource + "_for_" + singularFirstResource;
          return GenUtil.normalizeToLowerCamelCase(methodName);
        }
      }
    }

    // Find segments that are not resource IDs or path parameters
    // Skip the first segment as it's typically the resource collection name
    List<String> actionSegments = new ArrayList<>();
    String firstResourceSegment = null;
    boolean hasPathParam = false;
    boolean skipFirstSegment = true;
    for (String segment : pathSegments) {
      if (segment.contains("{")) {
        hasPathParam = true;
      } else if (!segment.isEmpty()) {
        if (skipFirstSegment) {
          skipFirstSegment = false;
          firstResourceSegment = segment;
          continue; // Skip the first segment (resource collection name)
        }
        actionSegments.add(segment);
      }
    }

    // If we have action segments, use them to build the method name
    if (!actionSegments.isEmpty()) {
      String methodName = String.join("_", actionSegments);
      String normalizedMethodName = GenUtil.normalizeToLowerCamelCase(methodName);
      
      // Handle Java reserved keywords by suffixing with resource name
      // e.g., /invoices/{id}/void -> voidInvoice (not voidForInvoice)
      if (JAVA_RESERVED_KEYWORDS.contains(normalizedMethodName.toLowerCase()) && firstResourceSegment != null) {
        String singularResource = singularize(firstResourceSegment);
        return normalizedMethodName + GenUtil.toClazName(singularResource);
      }
      
      // If the action is a CRUD verb that could conflict with simple CRUD operations,
      // append the resource name to disambiguate.
      // e.g., /unbilled_charges/create -> createUnbilledCharge (not just create)
      if (isCrudVerb(normalizedMethodName) && firstResourceSegment != null) {
        String singularResource = singularize(firstResourceSegment);
        return normalizedMethodName + GenUtil.toClazName(singularResource);
      }
      
      // If there's a path parameter, append the parent resource context to disambiguate
      // e.g., /customers/{id}/import_subscription -> importSubscriptionForCustomer
      // vs /subscriptions/import_subscription -> importSubscription
      if (hasPathParam && firstResourceSegment != null) {
        String singularResource = singularize(firstResourceSegment);
        return normalizedMethodName + "For" + GenUtil.toClazName(singularResource);
      }
      
      return normalizedMethodName;
    }

    // Fallback: use the last meaningful segment, skipping the first segment (resource collection)
    for (int i = pathSegments.length - 1; i > 0; i--) {
      String segment = pathSegments[i];
      if (!segment.contains("{") && !segment.isEmpty()) {
        return GenUtil.normalizeToLowerCamelCase(segment);
      }
    }

    // Ultimate fallback
    return "execute";
  }



  /**
   * Prefixes batch operations to avoid method name collisions.
   *
   * @param path the API path
   * @param methodName the derived method name
   * @return the method name, prefixed with "batch" if applicable
   */
  public static String applyBatchPrefix(String path, String methodName) {
    if (methodName != null && !methodName.isEmpty() && 
        path.startsWith("/batch/") && !methodName.startsWith("batch")) {
      return "batch" + methodName.substring(0, 1).toUpperCase() + methodName.substring(1);
    }
    return methodName;
  }

  /**
   * Converts a plural resource name to singular form.
   */
  private static String singularize(String plural) {
    if (plural.endsWith("ies")) {
      return plural.substring(0, plural.length() - 3) + "y";
    } else if (plural.endsWith("s") && !plural.endsWith("ss")) {
      return plural.substring(0, plural.length() - 1);
    }
    return plural;
  }

  /**
   * Checks if a method name is a standard CRUD verb that could conflict with 
   * auto-generated CRUD method names.
   */
  private static boolean isCrudVerb(String methodName) {
    return Set.of("create", "update", "delete", "list", "retrieve", "get").contains(methodName.toLowerCase());
  }

  /**
   * Checks if a segment corresponds to a known resource from the OpenAPI schema.
   * Converts the segment to various case formats and checks against schema names.
   */
  private static boolean isKnownResource(String segment) {
    if (schemaNames == null || schemaNames.isEmpty()) {
      return false;
    }
    
    // Convert segment to possible schema name formats
    // e.g., "orders" -> "order", "credit_notes" -> "credit_note", "CreditNote"
    String singular = singularize(segment);
    String upperCamel = GenUtil.toClazName(singular);
    
    // Check if any schema name matches
    return schemaNames.contains(upperCamel) || 
           schemaNames.contains(singular) ||
           schemaNames.contains(segment);
  }
}
