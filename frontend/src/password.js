export const PASSWORD_HINT = 'Минимум 10 символов: строчная и прописная буквы, цифра и спецсимвол.'

export function passwordError(password) {
  if ([...password].length < 10 || new TextEncoder().encode(password).length > 72
    || !/\p{Ll}/u.test(password) || !/\p{Lu}/u.test(password) || !/\p{Nd}/u.test(password)
    || !/[^\p{L}\p{N}\s]/u.test(password)) return `${PASSWORD_HINT} Не больше 72 байт UTF-8.`
  const letters = password.toLowerCase().replace(/[^a-zа-яё]/g, '')
  if (['password','qwerty','qwertyuiop','admin','administrator','welcome','letmein','iloveyou','travelapp','пароль'].includes(letters)) {
    return 'Это слишком распространённый пароль. Выберите менее очевидный.'
  }
  return ''
}
