const money = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 2 });
const request = async (path, options = {}) => {
    const response = await fetch(path, {
        ...options,
        headers: { 'Content-Type': 'application/json', ...(options.headers || {}) }
    });
    const body = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(body.detail || body.message || `Request failed (${response.status})`);
    return body;
};

const elements = {
    form: document.querySelector('#payment-form'),
    sender: document.querySelector('#sender'),
    receiver: document.querySelector('#receiver'),
    amount: document.querySelector('#amount'),
    pin: document.querySelector('#pin'),
    notice: document.querySelector('#notice'),
    track: document.querySelector('#mesh-track'),
    rounds: document.querySelector('#round-count'),
    packets: document.querySelector('#packet-list'),
    packetCount: document.querySelector('#packet-count'),
    accounts: document.querySelector('#account-list'),
    ledger: document.querySelector('#ledger-body'),
    ledgerCount: document.querySelector('#ledger-count'),
    gossip: document.querySelector('#gossip-button')
};

let accountOptions = [];

function announce(message, isError = false) {
    elements.notice.textContent = message;
    elements.notice.classList.toggle('error', isError);
}

function renderAccounts(accounts) {
    accountOptions = accounts;
    const optionMarkup = accounts.map(account => `<option value="${account.userId}">Phone ${account.userId}</option>`).join('');
    const previousSender = elements.sender.value || 'A';
    const previousReceiver = elements.receiver.value || 'B';
    elements.sender.innerHTML = optionMarkup;
    elements.receiver.innerHTML = optionMarkup;
    elements.sender.value = accounts.some(account => account.userId === previousSender) ? previousSender : accounts[0]?.userId;
    elements.receiver.value = accounts.some(account => account.userId === previousReceiver) ? previousReceiver : accounts[1]?.userId;
    syncReceiverOptions();
    elements.accounts.innerHTML = accounts.map(account => `
        <div class="account-row">
            <span class="account-id"><span class="account-avatar">${account.userId}</span>Phone ${account.userId}</span>
            <span class="account-balance">${money.format(account.balance)}</span>
        </div>`).join('');
}

function syncReceiverOptions() {
    [...elements.receiver.options].forEach(option => {
        option.disabled = option.value === elements.sender.value;
    });
    if (elements.receiver.value === elements.sender.value) {
        const next = accountOptions.find(account => account.userId !== elements.sender.value);
        if (next) elements.receiver.value = next.userId;
    }
}

function renderMesh(mesh) {
    elements.rounds.textContent = mesh.gossipRounds;
    const nodes = mesh.nodes;
    elements.track.innerHTML = nodes.map((node, index) => {
        const active = node.transactionIds.length > 0;
        const nodeMarkup = `<div class="phone-node ${node.bridge ? 'bridge' : ''} ${active ? 'has-packet' : ''}">
            <span class="phone-icon" aria-hidden="true">${node.bridge ? '↥' : '••'}</span>
            <span class="phone-name">${node.label}</span>
            <span class="phone-count">${node.transactionIds.length} packet${node.transactionIds.length === 1 ? '' : 's'}</span>
        </div>`;
        return index < nodes.length - 1 ? `${nodeMarkup}<span class="mesh-link" aria-hidden="true"></span>` : nodeMarkup;
    }).join('');

    const bridge = nodes.find(node => node.bridge);
    elements.packetCount.textContent = mesh.packets.length;
    if (mesh.packets.length === 0) {
        elements.packets.innerHTML = '<p class="empty-state">No packets yet. Create one to begin.</p>';
    } else {
        elements.packets.innerHTML = mesh.packets.map(packet => {
            const atBridge = bridge?.transactionIds.includes(packet.transactionId);
            const action = packet.uploaded
                ? `<button class="packet-action" disabled>${packet.status}</button>`
                : `<button class="packet-action" data-upload="${packet.transactionId}" ${atBridge ? '' : 'disabled'}>${atBridge ? 'Upload to backend' : 'Moving through mesh'}</button>`;
            return `<div class="packet-row"><div class="packet-main"><span class="packet-id">${packet.transactionId}</span><span class="packet-route">${packet.sender} → ${packet.receiver} · ${money.format(packet.amount)}</span></div>${action}</div>`;
        }).join('');
    }

    elements.packets.querySelectorAll('[data-upload]').forEach(button => {
        button.addEventListener('click', () => uploadPacket(button.dataset.upload, button));
    });
}

function renderLedger(transactions) {
    elements.ledgerCount.textContent = transactions.length;
    if (transactions.length === 0) {
        elements.ledger.innerHTML = '<tr><td class="empty-cell" colspan="6">No settled transactions yet.</td></tr>';
        return;
    }
    elements.ledger.innerHTML = transactions.map(transaction => `
        <tr><td>${transaction.transactionId}</td><td>${transaction.sender}</td><td>${transaction.receiver}</td>
        <td>${money.format(transaction.amount)}</td><td>${new Date(transaction.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</td>
        <td><span class="status-pill">${transaction.status}</span></td></tr>`).join('');
}

async function refresh() {
    const state = await request('/api/demo/state');
    renderAccounts(state.accounts);
    renderMesh(state.mesh);
    renderLedger(state.transactions);
}

async function uploadPacket(transactionId, button) {
    button.disabled = true;
    try {
        const receipt = await request(`/api/demo/upload/${encodeURIComponent(transactionId)}`, { method: 'POST' });
        announce(`${receipt.transactionId} settled · ${money.format(receipt.amount)} moved from ${receipt.sender} to ${receipt.receiver}.`);
        await refresh();
    } catch (error) {
        announce(error.message, true);
        button.disabled = false;
    }
}

elements.sender.addEventListener('change', syncReceiverOptions);
elements.form.addEventListener('submit', async event => {
    event.preventDefault();
    const button = elements.form.querySelector('button[type="submit"]');
    button.disabled = true;
    announce('Sealing packet and injecting it at the sender phone…');
    try {
        await request('/api/demo/payments', {
            method: 'POST',
            body: JSON.stringify({
                sender: elements.sender.value,
                receiver: elements.receiver.value,
                amount: elements.amount.value,
                pin: elements.pin.value
            })
        });
        elements.pin.value = '';
        announce('Encrypted packet injected. Run gossip rounds until it reaches the bridge.');
        await refresh();
    } catch (error) {
        announce(error.message, true);
    } finally {
        button.disabled = false;
    }
});

elements.gossip.addEventListener('click', async () => {
    elements.gossip.disabled = true;
    try {
        await request('/api/demo/gossip', { method: 'POST' });
        await refresh();
    } catch (error) {
        announce(error.message, true);
    } finally {
        elements.gossip.disabled = false;
    }
});

document.querySelector('#reset-button').addEventListener('click', async () => {
    try {
        await request('/api/demo/reset', { method: 'POST' });
        announce('Mesh cleared. Settled balances and ledger were kept.');
        await refresh();
    } catch (error) {
        announce(error.message, true);
    }
});

refresh().catch(error => announce(error.message, true));
