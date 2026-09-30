const $ = (id) => document.getElementById(id);
const state = { categories: [], editingId: null, page: 1 };

// Build DOM with textContent (never innerHTML) so user-entered text can't inject HTML/JS.
function el(tag, props = {}, ...kids) {
  const n = document.createElement(tag);
  Object.assign(n, props);
  kids.forEach((k) => n.append(k));
  return n;
}
function today() { return new Date(Date.now() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 10); }
function flash(text, isError) {
  const m = $('msg');
  m.textContent = text;
  m.className = 'msg' + (isError ? '' : ' ok');
  if (text) setTimeout(() => { if (m.textContent === text) m.textContent = ''; }, 4000);
}
function fail(e) {
  if (e.status === 401) location.replace('login.html');
  else flash(e.message, true);
}

// ---------- categories ----------
async function loadCategories() {
  state.categories = await api('api/categories');
  const filter = $('filterCategory'), form = $('category'), keep = filter.value;
  filter.replaceChildren(new Option('All categories', ''), new Option('Uncategorized', '0'));
  form.replaceChildren(new Option('Uncategorized', ''));
  state.categories.forEach((c) => {
    filter.add(new Option(c.name, c.id));
    form.add(new Option(c.name, c.id));
  });
  filter.value = keep;
  if (filter.selectedIndex < 0) filter.value = '';

  const list = $('catList');
  list.replaceChildren();
  state.categories.forEach((c) => {
    const dot = el('span', { className: 'dot' });
    dot.style.background = c.color;
    list.append(el('li', {}, dot, el('span', { textContent: c.name }),
      el('button', { className: 'btn btn-small btn-ghost', type: 'button', textContent: 'Delete', onclick: () => removeCategory(c) })));
  });
}
async function removeCategory(c) {
  if (!confirm('Delete "' + c.name + '"? Its expenses will become Uncategorized.')) return;
  try { await api('api/categories/' + c.id, 'DELETE'); await loadCategories(); await loadExpenses(); flash('Category deleted'); }
  catch (e) { fail(e); }
}
$('catForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  try {
    await api('api/categories', 'POST', { name: $('catName').value, color: $('catColor').value });
    $('catName').value = '';
    await loadCategories();
    flash('Category added');
  } catch (err) { fail(err); }
});

// ---------- expenses ----------
const FILTER_IDS = ['fSearch', 'filterCategory', 'fFrom', 'fTo', 'fMin', 'fMax'];
function queryString() {
  const p = new URLSearchParams();
  const add = (k, v) => { if (v !== '' && v != null) p.set(k, v); };
  add('q', $('fSearch').value.trim());
  add('categoryId', $('filterCategory').value);
  add('from', $('fFrom').value);
  add('to', $('fTo').value);
  add('min', $('fMin').value);
  add('max', $('fMax').value);
  const [sort, dir] = $('fSort').value.split(':');
  add('sort', sort);
  add('dir', dir);
  add('page', state.page);
  add('size', 10);
  return p.toString();
}
async function loadExpenses() {
  const data = await api('api/expenses?' + queryString());
  state.page = data.page;
  $('total').textContent = inr.format(data.total);
  $('count').textContent = data.count;
  const filtered = FILTER_IDS.some((id) => $(id).value !== '');
  $('empty').textContent = filtered ? 'No expenses match these filters.' : 'No expenses yet. Add your first one above.';
  $('empty').hidden = data.count > 0;
  $('pageInfo').textContent = 'Page ' + data.page + ' of ' + data.pages;
  $('prevBtn').disabled = data.page <= 1;
  $('nextBtn').disabled = data.page >= data.pages;
  const body = $('rows');
  body.replaceChildren();
  data.expenses.forEach((x) => body.append(row(x)));
}
function row(x) {
  const badge = el('span', { className: 'badge', textContent: x.categoryName || 'Uncategorized' });
  if (x.categoryColor) badge.style.background = x.categoryColor;
  const details = el('td', {}, el('strong', { textContent: x.title }));
  if (x.note) details.append(el('div', { className: 'muted small', textContent: x.note }));
  return el('tr', {},
    el('td', { textContent: x.date }), details, el('td', {}, badge),
    el('td', { className: 'num', textContent: inr.format(x.amount) }),
    el('td', { className: 'actions' },
      el('button', { className: 'btn btn-small btn-ghost', type: 'button', textContent: 'Edit', onclick: () => startEdit(x) }),
      el('button', { className: 'btn btn-small btn-danger', type: 'button', textContent: 'Delete', onclick: () => removeExpense(x) })));
}
function resetForm() {
  state.editingId = null;
  $('expenseForm').reset();
  $('date').value = today();
  $('formTitle').textContent = 'Add expense';
  $('saveBtn').textContent = 'Add expense';
  $('cancelBtn').hidden = true;
}
function startEdit(x) {
  state.editingId = x.id;
  $('title').value = x.title;
  $('amount').value = x.amount;
  $('date').value = x.date;
  $('category').value = x.categoryId == null ? '' : x.categoryId;
  $('note').value = x.note || '';
  $('formTitle').textContent = 'Edit expense';
  $('saveBtn').textContent = 'Save changes';
  $('cancelBtn').hidden = false;
  $('title').scrollIntoView({ behavior: 'smooth', block: 'center' });
  $('title').focus();
}
async function removeExpense(x) {
  if (!confirm('Delete "' + x.title + '"?')) return;
  try { await api('api/expenses/' + x.id, 'DELETE'); if (state.editingId === x.id) resetForm(); await loadExpenses(); flash('Expense deleted'); }
  catch (e) { fail(e); }
}
$('expenseForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  const body = {
    title: $('title').value,
    amount: Number($('amount').value),
    date: $('date').value,
    categoryId: $('category').value ? Number($('category').value) : null,
    note: $('note').value
  };
  try {
    if (state.editingId) await api('api/expenses/' + state.editingId, 'PUT', body);
    else await api('api/expenses', 'POST', body);
    flash(state.editingId ? 'Expense updated' : 'Expense added');
    resetForm();
    await loadExpenses();
  } catch (err) { fail(err); }
});
$('cancelBtn').addEventListener('click', resetForm);
function refilter() { state.page = 1; loadExpenses().catch(fail); }
function debounce(fn, ms) { let t; return () => { clearTimeout(t); t = setTimeout(fn, ms); }; }
['filterCategory', 'fFrom', 'fTo', 'fSort'].forEach((id) => $(id).addEventListener('change', refilter));
['fSearch', 'fMin', 'fMax'].forEach((id) => $(id).addEventListener('input', debounce(refilter, 300)));
$('fClear').addEventListener('click', () => {
  FILTER_IDS.forEach((id) => { $(id).value = ''; });
  $('fSort').value = 'date:desc';
  refilter();
});
$('prevBtn').addEventListener('click', () => { state.page--; loadExpenses().catch(fail); });
$('nextBtn').addEventListener('click', () => { state.page++; loadExpenses().catch(fail); });

// ---------- session ----------
$('logoutBtn').addEventListener('click', async () => {
  try { await api('api/auth/logout', 'POST'); } finally { location.replace('login.html'); }
});

(async function init() {
  try { $('userName').textContent = (await api('api/auth/me')).name; }
  catch (e) { location.replace('login.html'); return; }
  resetForm();
  await loadCategories();
  await loadExpenses();
})().catch(fail);
