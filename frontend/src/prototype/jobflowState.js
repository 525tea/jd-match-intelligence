let currentJobflowState = {};

export function setJobflowState(state) {
  currentJobflowState = state || {};
  if (import.meta.env.DEV) {
    window.JF = currentJobflowState;
  }
}

export function getJobflowState() {
  return currentJobflowState;
}
