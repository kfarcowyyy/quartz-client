function addQuartzPicker(select) {
  const root = document.createElement('div');
  const trigger = document.createElement('button');
  const popup = document.createElement('div');
  root.className = 'quartz-picker';
  trigger.type = 'button';
  trigger.className = 'quartz-picker-trigger';
  trigger.setAttribute('aria-haspopup', 'listbox');
  trigger.setAttribute('aria-expanded', 'false');
  trigger.setAttribute('aria-label', select.getAttribute('aria-label') || select.closest('label')?.firstChild?.textContent.trim() || '');
  popup.className = 'quartz-picker-popup';
  popup.id = select.id + '-choices';
  popup.setAttribute('popover', 'auto');
  popup.setAttribute('role', 'listbox');
  trigger.setAttribute('aria-controls', popup.id);
  select.before(root);
  select.hidden = true;
  root.append(select, trigger, popup);

  const options = () => [...popup.querySelectorAll('button:not(:disabled)')];
  function refreshLabel() {
    trigger.disabled = select.disabled;
    trigger.textContent = select.selectedOptions[0]?.textContent.trim() || '…';
    if (trigger.disabled && popup.matches(':popover-open')) popup.hidePopover();
  }

  function open() {
    if (trigger.disabled || popup.matches(':popover-open')) return;
    popup.replaceChildren();
    const add = option => {
      const item = document.createElement('button');
      item.type = 'button';
      item.className = 'quartz-picker-option';
      item.setAttribute('role', 'option');
      item.setAttribute('aria-selected', String(option.selected));
      item.disabled = option.disabled || option.parentElement.disabled;
      item.textContent = option.textContent.trim();
      item.addEventListener('click', () => {
        const changed = select.value !== option.value;
        select.selectedIndex = option.index;
        refreshLabel();
        popup.hidePopover();
        trigger.focus();
        if (changed) select.dispatchEvent(new Event('change', { bubbles: true }));
      });
      popup.append(item);
    };
    for (const entry of select.children) {
      if (entry.tagName === 'OPTGROUP') {
        const title = document.createElement('div');
        title.className = 'quartz-picker-group';
        title.textContent = entry.label;
        popup.append(title);
        for (const option of entry.children) add(option);
      } else add(entry);
    }
    const bounds = trigger.getBoundingClientRect();
    const above = bounds.top - 10, below = innerHeight - bounds.bottom - 10;
    const up = below < 220 && above > below;
    popup.style.left = Math.min(bounds.left, innerWidth - Math.max(bounds.width, 170) - 10) + 'px';
    popup.style.minWidth = Math.max(bounds.width, 170) + 'px';
    popup.style.maxHeight = Math.min(332, up ? above - 5 : below - 5) + 'px';
    popup.style.top = up ? 'auto' : (bounds.bottom + 5) + 'px';
    popup.style.bottom = up ? (innerHeight - bounds.top + 5) + 'px' : 'auto';
    popup.showPopover();
    (popup.querySelector('[aria-selected="true"]:not(:disabled)') || options()[0])?.focus();
  }

  trigger.addEventListener('click', () => popup.matches(':popover-open') ? popup.hidePopover() : open());
  trigger.addEventListener('keydown', event => {
    if (!['ArrowDown', 'ArrowUp'].includes(event.key)) return;
    event.preventDefault();
    open();
  });
  popup.addEventListener('keydown', event => {
    const items = options(), at = items.indexOf(document.activeElement);
    const key = event.key;
    if (key === 'Escape') { event.preventDefault(); popup.hidePopover(); trigger.focus(); return; }
    const index = key === 'ArrowDown' ? Math.min(at + 1, items.length - 1)
      : key === 'ArrowUp' ? Math.max(at - 1, 0) : key === 'Home' ? 0 : key === 'End' ? items.length - 1 : -1;
    if (index >= 0) { event.preventDefault(); items[index].focus(); }
  });
  popup.addEventListener('toggle', event => trigger.setAttribute('aria-expanded', String(event.newState === 'open')));
  select.addEventListener('change', refreshLabel);
  new MutationObserver(refreshLabel).observe(select, { childList: true, subtree: true, attributes: true });
  refreshLabel();
}
