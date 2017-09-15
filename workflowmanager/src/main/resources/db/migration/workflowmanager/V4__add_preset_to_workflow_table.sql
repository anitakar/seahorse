CREATE TABLE IF NOT EXISTS WORKFLOWSPRESETS(
  "workflow_id" UUID NOT NULL,
  "preset_id" BIGINT NOT NULL,
  PRIMARY KEY (workflow_id),
  FOREIGN KEY (workflow_id) REFERENCES WORKFLOWS(id) ON DELETE CASCADE,
  FOREIGN KEY (preset_id) REFERENCES PRESETS(id) ON DELETE CASCADE);