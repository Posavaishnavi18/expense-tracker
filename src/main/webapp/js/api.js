// Shared helper. Relative URLs so the app works under any context path (e.g. /expense-tracker/).
async function api(path, method = 'GET', body) {
  const res = await fetch(path, {
    method,
    credentials: 'same-origin',
    headers: { 'Content-Type': 'application/json', 'X-Requested-With': 'XMLHttpRequest' },
    body: body === undefined ? undefined : JSON.stringify(body)
  });
  let data = null;
  try { data = await res.json(); } catch (e) { /* empty body */ }
  if (!res.ok) {
    const err = new Error((data && data.error) || 'Request failed (' + res.status + ')');
    err.status = res.status;
    throw err;
  }
  return data;
}
const inr = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' });
