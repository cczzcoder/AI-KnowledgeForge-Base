export interface GlobalState {
  username?: string;
  token?: string;
  roles?: string[];
}

export const initialGlobalState: GlobalState = {
  username: undefined,
  token: localStorage.getItem('token') || undefined,
  roles: [],
};
