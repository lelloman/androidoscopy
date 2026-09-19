<script lang="ts">
    import { onMount } from 'svelte';
    type Tool = { name: string; description: string; inputSchema: unknown };
    type Device = { id: string; address: string; status: string; code?: string; error?: string; tools: Tool[] };
    let devices: Device[] = [];
    let address = '';
    let error = '';
    let selectedDevice = '';
    let selectedTool = '';
    let argumentsText = '{}';
    let result = '';
    let busy = false;
    async function refresh() {
        try { const response = await fetch('/api/v2/devices'); if (response.ok) devices = await response.json(); }
        catch { /* Reconnect is handled by the main dashboard. */ }
    }
    async function post(path: string, body: unknown) {
        const response = await fetch(`/api/v2/${path}`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
        if (!response.ok) throw new Error(await response.text());
        return response.json();
    }
    async function attach(target: string) {
        try { await post('connect', { address: target }); error = ''; await refresh(); }
        catch (e) { error = String(e); }
    }
    async function invoke() {
        busy = true;
        try { result = JSON.stringify(await post('call', { device: selectedDevice, name: selectedTool, arguments: JSON.parse(argumentsText) }), null, 2); }
        catch (e) { result = String(e); }
        finally { busy = false; }
    }
    onMount(() => { void refresh(); const timer = setInterval(refresh, 1000); return () => clearInterval(timer); });
</script>

<section>
    <h2>LAN devices</h2>
    <form onsubmit={(event) => { event.preventDefault(); void attach(address); }}>
        <input aria-label="Device IP and port" placeholder="Device IP:port" bind:value={address} />
        <button type="submit">Connect</button>
    </form>
    {#if error}<p role="alert">{error}</p>{/if}
    {#each devices as device (device.id)}
        <article>
            <strong>{device.id}</strong> · {device.address} · {device.status}
            {#if device.status === 'pairing'}
                <p>Compare this number with the phone, then approve there: <strong class="code">{device.code}</strong></p>
                <button onclick={() => post('disconnect', { device: device.id }).catch(e => error = String(e))}>Cancel pairing</button>
            {:else if device.status === 'connected'}
                <button onclick={() => post('disconnect', { device: device.id }).catch(e => error = String(e))}>Disconnect</button>
                <details>
                    <summary>App tools ({device.tools.length})</summary>
                    {#each device.tools as tool (tool.name)}
                        <p><button onclick={() => { selectedDevice = device.id; selectedTool = tool.name; result = ''; }}>{tool.name}</button> — {tool.description}</p>
                        <pre>{JSON.stringify(tool.inputSchema, null, 2)}</pre>
                    {/each}
                </details>
            {:else}<button onclick={() => attach(device.address)}>Connect</button>{/if}
            {#if device.error}<p>{device.error}</p>{/if}
            <button onclick={() => post('forget', { device: device.id }).catch(e => error = String(e))}>Forget pairing</button>
        </article>
    {/each}
    {#if selectedTool}
        <h3>{selectedTool}</h3>
        <textarea aria-label="Tool arguments (JSON)" bind:value={argumentsText}></textarea>
        <button disabled={busy} onclick={invoke}>Run tool</button>
        <pre>{result}</pre>
    {/if}
</section>

<style>
    section { margin-bottom: 2rem; padding: 1rem; border: 1px solid #444; border-radius: 8px; }
    article { padding: 1rem 0; border-bottom: 1px solid #333; }
    input, textarea, button { padding: .5rem; margin: .25rem; }
    textarea { display: block; width: min(100%, 40rem); min-height: 5rem; }
    .code { font-size: 2rem; letter-spacing: .15em; }
    pre { white-space: pre-wrap; overflow-wrap: anywhere; }
</style>
