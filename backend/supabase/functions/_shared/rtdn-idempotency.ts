export type DuplicateEventAction = "acknowledge" | "resume";

/**
 * A Pub/Sub message is safe to acknowledge only after its audit row is processed.
 * Unprocessed rows must be resumed so transient failures do not strand entitlements.
 */
export function duplicateEventAction(processed: unknown): DuplicateEventAction {
  return processed === true ? "acknowledge" : "resume";
}
