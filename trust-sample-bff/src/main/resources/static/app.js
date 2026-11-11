// The browser never sees a token: only the BFF's session cookie.
// Writes carry the CSRF token the BFF puts in the XSRF-TOKEN cookie.

function cookie(name) {
  const entry = document.cookie.split('; ').find(row => row.startsWith(name + '='));
  return entry ? decodeURIComponent(entry.substring(name.length + 1)) : '';
}

function show(title, ok, body) {
  document.getElementById('result-panel').hidden = false;
  const heading = document.getElementById('result-title');
  heading.textContent = title;
  heading.className = ok ? 'ok' : 'ko';
  document.getElementById('result').textContent = body;
}

// RFC 9470 challenge sent back by an API or by the BFF itself.
function requiredAcr(response) {
  const challenge = response.headers.get('WWW-Authenticate') || '';
  if (!challenge.includes('insufficient_user_authentication')) return null;
  const match = challenge.match(/acr_values="([^"]+)"/);
  return match ? match[1] : null;
}

async function call(method, path) {
  const response = await fetch(path, {
    method,
    headers: { 'X-XSRF-TOKEN': cookie('XSRF-TOKEN'), 'Accept': 'application/json' },
    redirect: 'manual'
  });
  const text = await response.text();
  let body = text;
  try { body = JSON.stringify(JSON.parse(text), null, 2); } catch (ignored) { /* not JSON */ }

  const acr = requiredAcr(response);
  if (response.status === 401 && acr) {
    show(`${method} ${path} → 401: step-up to acr=${acr} required`, false, body);
    if (confirm(`This action requires a stronger authentication (acr=${acr}). Continue?`)) {
      const returnTo = encodeURIComponent('/');
      window.location.href = `/oauth2/step-up?acr=${encodeURIComponent(acr)}&returnTo=${returnTo}`;
    }
    return;
  }
  if (response.type === 'opaqueredirect') {
    show(`${method} ${path} → session expired`, false, 'Log in again.');
    return;
  }
  show(`${method} ${path} → ${response.status}`, response.ok, body);
}

async function init() {
  const session = await (await fetch('/api/session')).json();
  document.getElementById('anonymous').hidden = session.authenticated;
  document.getElementById('authenticated').hidden = !session.authenticated;
  document.getElementById('session').textContent = session.authenticated
    ? `${session.user} · acr=${session.acr} · ${session.authorities.join(', ') || 'no role'}`
    : 'not logged in';

  document.getElementById('csrf').value = cookie('XSRF-TOKEN');
  document.querySelectorAll('[data-call]').forEach(button => {
    const [method, path] = button.dataset.call.split(' ');
    button.addEventListener('click', () => call(method, path));
  });
}

init();
