let actions = {};

export function setJobflowActions(nextActions) {
  actions = nextActions || {};
  if (import.meta.env.DEV) {
    window.__jobflowApi = actions;
  }
}

export function clearJobflowActions() {
  actions = {};
  if (import.meta.env.DEV) {
    delete window.__jobflowApi;
  }
}

export const jobflowActions = new Proxy({}, {
  get(_target, property) {
    return actions[property];
  },
});
