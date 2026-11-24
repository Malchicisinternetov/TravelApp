import { createContext, useContext, useEffect, useState } from 'react'

export const AppContext = createContext(null)
export function useApp() { return useContext(AppContext) }
export function useParticipantNames(ids) {
  const { api, rememberUsers } = useApp()
  const key = [...new Set(ids)].sort((a,b) => a-b).join(',')
  useEffect(() => {
    if (!key) return
    let active = true
    api.userSummaries(key.split(',').map(Number)).then(users => { if (active) rememberUsers(users) }).catch(() => {})
    return () => { active = false }
  }, [api, key, rememberUsers])
}
export function useRemote(load, dependencies) {
  const [state, setState] = useState({ data: null, loading: true, error: '' })
  const [version, setVersion] = useState(0)
  useEffect(() => {
    let active = true
    setState({ data: null, loading: true, error: '' })
    Promise.resolve().then(load).then(data => {
      if (active) setState({ data, loading: false, error: '' })
    }).catch(error => {
      if (active) setState({ data: null, loading: false, error: error.message })
    })
    return () => { active = false }
  // Resource identity is supplied by the caller; ignore responses after navigation/unmount.
  }, [...dependencies, version])
  return { ...state, reload: () => setVersion(v => v + 1), setData: data => setState({ data, loading: false, error: '' }) }
}
