package com.chargebee.sdk.changelog.models;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single rendered changelog line together with structured metadata that allows the docs
 * availability checker to verify whether the referenced entity actually exists in the local docs
 * repository.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangeLogEntry {

  /** Rendered line as it should appear in the docs index. Consumed by the Handlebars template. */
  private String line;

  /** Kind of change this entry represents; drives the availability check logic. */
  private EntryType type;

  /** Singular snake-case resource id (matches the directory name under {@code v2-pcv2/}). */
  private String resourceId;

  /**
   * Plural snake-case slug used in the generated {@code [link_api ...]} URL and expected to match
   * an {@code href: /docs/api/<slug>} entry in the docs {@code TOC.yaml} (i.e. the resource is
   * actually published).
   */
  private String docsResourcePath;

  /** Snake-case action id (matches the action yaml file name under the resource directory). */
  private String actionId;

  /**
   * Dot-path slug of the attribute or parameter, exactly as it should appear in the resource or
   * action {@code .yaml} as {@code slugId:}.
   */
  private String slugPath;

  /** Changed enum values, used by enum-add/remove entries. */
  private List<String> enumValues;

  /** Webhook event type, used by event-type entries. */
  private String eventType;

  /**
   * For enum entries only: the {@code link_api} target before the {@code #} (e.g.
   * {@code omnichannel_subscriptions/list-omnichannel-subscriptions}). Stored so the line can be
   * re-rendered for a subset of {@link #enumValues} when only some values are documented.
   */
  private String linkPath;

  /** For enum entries only: the anchor used after {@code #} in the {@code link_api} target. */
  private String linkAnchor;

  /** For enum entries only: the second {@code [code ...]} label (the path to the enum's owner). */
  private String codeLabel;

  public enum EntryType {
    NEW_RESOURCE,
    NEW_ACTION,
    NEW_ATTRIBUTE,
    NEW_PARAMETER,
    NEW_EVENT_TYPE,
    DELETED_RESOURCE,
    DELETED_ACTION,
    DELETED_ATTRIBUTE,
    DELETED_PARAMETER,
    DELETED_EVENT_TYPE,
    NEW_ATTRIBUTE_ENUM_VALUE,
    DELETED_ATTRIBUTE_ENUM_VALUE,
    NEW_PARAMETER_ENUM_VALUE,
    DELETED_PARAMETER_ENUM_VALUE,
    PARAMETER_REQUIREMENT_CHANGE,
    DEPRECATED_RESOURCE,
    DEPRECATED_ACTION,
    DEPRECATED_ATTRIBUTE,
    DEPRECATED_PARAMETER
  }
}
