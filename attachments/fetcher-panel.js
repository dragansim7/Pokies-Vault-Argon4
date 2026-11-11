/* Bonus Fetcher panel: the fetcher page script, booted inside the homepage panel. */
window.BPLFetcherBoot = function (cfg) {
"use strict";

const API="https://fetch.bestpokieslist.online";
const PAGE_SIZE=25;
const $=id=>document.getElementById(id);
const SAVED_CREDENTIALS_KEY="bestpokies_bonus_fetch_credentials_v1";

function readSavedCredentials(){
    try{
        const parsed=JSON.parse(localStorage.getItem(SAVED_CREDENTIALS_KEY)||"{}");
        return parsed && typeof parsed==="object" ? parsed : {};
    }catch(_){ return {}; }
}

function collectFallbackCredentials(){
    const pairs=[
        [fallbackPhone2?.value?.trim?.()||"", fallbackPassword2?.value||""],
        [fallbackPhone3?.value?.trim?.()||"", fallbackPassword3?.value||""],
        [fallbackPhone4?.value?.trim?.()||"", fallbackPassword4?.value||""]
    ];

    return pairs
        .filter(([phone,password])=>phone && password)
        .map(([phone,password])=>({phone,password}));
}

function saveCredentialsLocally(){
    const phone=phoneInput?.value?.trim?.()||"";
    const password=passwordInput?.value||"";
    if(!phone) return;

    try{
        const saved=readSavedCredentials();
        const accounts=(saved.accounts && typeof saved.accounts==="object") ? saved.accounts : {};

        accounts[phone]={
            password,
            fallbacks:[
                {phone:fallbackPhone2?.value?.trim?.()||"",password:fallbackPassword2?.value||""},
                {phone:fallbackPhone3?.value?.trim?.()||"",password:fallbackPassword3?.value||""},
                {phone:fallbackPhone4?.value?.trim?.()||"",password:fallbackPassword4?.value||""}
            ]
        };

        localStorage.setItem(
            SAVED_CREDENTIALS_KEY,
            JSON.stringify({lastPhone:phone,accounts})
        );
    }catch(e){
        console.warn("Could not save credentials locally",e);
    }
}

function applySavedCredentialEntry(entry){
    if(!entry || typeof entry!=="object") return;

    passwordInput.value=String(entry.password||"");

    const f=Array.isArray(entry.fallbacks) ? entry.fallbacks : [];

    fallbackPhone2.value=String(f[0]?.phone||"");
    fallbackPassword2.value=String(f[0]?.password||"");
    fallbackPhone3.value=String(f[1]?.phone||"");
    fallbackPassword3.value=String(f[1]?.password||"");
    fallbackPhone4.value=String(f[2]?.phone||"");
    fallbackPassword4.value=String(f[2]?.password||"");
}

function restoreCredentialsLocally(){
    const saved=readSavedCredentials();
    const phone=String(saved.lastPhone||"");
    const accounts=(saved.accounts && typeof saved.accounts==="object") ? saved.accounts : {};

    if(phone){
        phoneInput.value=phone;
        applySavedCredentialEntry(accounts[phone]);
    }
}

function restorePasswordForPhone(){
    const phone=phoneInput.value.trim();
    if(!phone) return;

    const saved=readSavedCredentials();
    const entry=saved?.accounts?.[phone];

    if(entry && typeof entry==="object"){
        applySavedCredentialEntry(entry);
    }
}


const runBtn=$("runBtn");
const stopBtn=$("stopBtn");
const lastFetchBtn=$("lastFetchBtn");
const phoneInput=$("phoneInput");
const passwordInput=$("passwordInput");
const togglePassword=$("togglePassword");
const advancedCredsBtn=$("advancedCredsBtn");
const advancedCreds=$("advancedCreds");
const fallbackPhone2=$("fallbackPhone2");
const fallbackPassword2=$("fallbackPassword2");
const fallbackPhone3=$("fallbackPhone3");
const fallbackPassword3=$("fallbackPassword3");
const fallbackPhone4=$("fallbackPhone4");
const fallbackPassword4=$("fallbackPassword4");
const statusPanel=$("statusPanel");
const statusTitle=$("statusTitle");
const statusDetail=$("statusDetail");
const statusSub=$("statusSub");
const progressFill=$("progressFill");
const finishLine=$("finishLine");
const resultsPanel=$("resultsPanel");
const resultsTitle=$("resultsTitle");
const resultsBody=$("resultsBody");
const sortSelect=$("sortSelect");
const pageInfo=$("pageInfo");
const prevBtn=$("prevBtn");
const nextBtn=$("nextBtn");
const openPageBtn=$("openPageBtn");
const noDataPanel=$("noDataPanel");
const noDataTitle=$("noDataTitle");
const noDataBody=$("noDataBody");
const errorsPanel=$("errorsPanel");
const errorsTitle=$("errorsTitle");
const errorsBody=$("errorsBody");
const quotaLine=$("quotaLine");
const buyFetchesBtn=$("buyFetchesBtn");
const fetchShopIntro=$("fetchShopIntro");
const fetchPurchaseStatus=$("fetchPurchaseStatus");
const fetchPackageBtns=[
    ...document.querySelectorAll(
        "[data-fetch-package]"
    )
];
const cacheStamp=$("cacheStamp");
const changeSummary=$("changeSummary");
const changeSummaryItems=$("changeSummaryItems");
const limitModal=$("limitModal");
const closeLimitModal=$("closeLimitModal");

const setupGuide=document.querySelector(".setupGuide");

const credentialPanel=
    document.querySelector(".credentialTitle")
        ?.closest("section")
    || document.querySelector(".credentialTitle")
        ?.parentElement
    || null;

const fetchActionsPanel=
    runBtn?.closest(".fetchActions")
    || document.querySelector(".fetchActions");

let fetchAccessRemaining=null;


function displayedFetchCount(){
    if(
        Number.isFinite(
            Number(fetchAccessRemaining)
        )
    ){
        return Math.max(
            0,
            Number(fetchAccessRemaining)
        );
    }

    const match=
        String(
            quotaLine?.textContent
            || ""
        ).match(/(\d+)\s+Fetch/i);

    if(match){
        return Math.max(
            0,
            Number(match[1])
        );
    }

    return null;
}


function applyFetchAccessVisibility(
    remaining
){
    remaining=Math.max(
        0,
        Number(remaining)||0
    );

    fetchAccessRemaining=
        remaining;

    /*
     * The complete Bonus Fetcher ALWAYS stays visible,
     * including when the account has 0 fetches.
     */
    credentialPanel
        ?.classList.remove("hidden");

    fetchActionsPanel
        ?.classList.remove("hidden");

    quotaLine
        ?.classList.remove("hidden");

    buyFetchesBtn
        ?.classList.remove("hidden");

    /*
     * Permanently remove the incorrect zero-fetch card
     * if an old instance somehow exists in the DOM.
     */
    document
        .getElementById("zeroFetchPanel")
        ?.remove();

    /*
     * At 0 fetches we only hide fetch-progress UI.
     *
     * Credentials, Fetch Bonus Data, View Last Fetch,
     * quota count and Buy Fetches all remain visible.
     */
    if(remaining<=0){
        statusPanel
            ?.classList.add("hidden");

        stopBtn
            ?.classList.add("hidden");

        if(statusTitle){
            statusTitle.textContent=
                "Preparing fetch…";
        }

        if(statusDetail){
            statusDetail.innerHTML="";
        }

        if(statusSub){
            statusSub.textContent="";
        }

        if(progressFill){
            progressFill.style.width="0%";
        }

        lastStatusRender={
            title:null,
            detail:null,
            sub:null,
            state:null,
            progress:null
        };
    }
}


/*
 * quotaLine is already updated by the existing quota code.
 * Observe that single authoritative display and keep the
 * fetch form in sync with it.
 */
if(quotaLine){
    const syncFetchAccessFromQuotaLine=()=>{
        const match=
            String(
                quotaLine.textContent
                || ""
            ).match(/(\d+)\s+Fetch/i);

        if(match){
            applyFetchAccessVisibility(
                Number(match[1])
            );
        }
    };

    new MutationObserver(
        syncFetchAccessFromQuotaLine
    ).observe(
        quotaLine,
        {
            childList:true,
            subtree:true,
            characterData:true
        }
    );

    syncFetchAccessFromQuotaLine();
}

// Guarantee live status/results stay directly under Fetch access and the

function enforceFetchLayoutOrder(){
    const main=document.querySelector("main");
    if(!main || !setupGuide || !limitModal) return;
    if(setupGuide.nextElementSibling!==limitModal){
        main.insertBefore(setupGuide,limitModal);
    }
}
enforceFetchLayoutOrder();


/*
 * Fetch button doubles as the website Login button.
 *
 * If PHP already has a valid remembered/session login,
 * the first click can fetch immediately.
 *
 * Otherwise the first click only authenticates the
 * website account and consumes NO fetch.
 */
let fetchLoginConfirmed = !!cfg.loggedIn;

function updateRunButtonLabel(){
    if(!runBtn) return;

    runBtn.textContent =
        fetchLoginConfirmed
            ? "Fetch Bonus Data"
            : "🔐 Sign Up / Log In";
}

updateRunButtonLabel();

let rows=[];
let emptyRows=[];
let displayRows=[];
let currentPage=1;
let currentJobId=null;
let currentQueueId=null;
let fetchStarted=0;
let stopped=false;
let lastRescueRecovered=-1;


// values only control how quickly this page reflects changes from the worker.
const POLL_ACTIVE_MS=200;
const POLL_RESCUE_MS=250;
const POLL_QUEUE_MS=1500;
let resultsRefreshPromise=null;

const esc=value=>String(value??"")
    .replaceAll("&","&amp;").replaceAll("<","&lt;").replaceAll(">","&gt;")
    .replaceAll('"',"&quot;").replaceAll("'","&#039;");

const money=value=>{
    if(value===null || value===undefined || value==="") return "—";
    const n=Number(value);
    return Number.isFinite(n) ? `$${n.toFixed(2)}` : "—";
};

let lastStatusRender={title:null,detail:null,sub:null,state:null,progress:null};

function setStatus(title,detail="",sub="",progress=null,state=""){
    statusPanel.classList.remove("hidden","is-quiet");

    if(lastStatusRender.title!==title){
        statusTitle.textContent=title;
        lastStatusRender.title=title;
    }

    if(lastStatusRender.state!==state){
        statusTitle.className="statusTitle"+(state?` ${state}`:"");
        lastStatusRender.state=state;
    }

    if(lastStatusRender.detail!==detail){
        statusDetail.innerHTML=detail;
        lastStatusRender.detail=detail;
    }

    if(lastStatusRender.sub!==sub){
        statusSub.textContent=sub;
        lastStatusRender.sub=sub;
    }

    if(progress!==null){
        const p=Math.max(0,Math.min(100,Number(progress)||0));
        if(lastStatusRender.progress===null || Math.abs(lastStatusRender.progress-p)>=0.03){
            progressFill.style.width=`${p}%`;
            lastStatusRender.progress=p;
        }
    }
}

async function apiJson(path,options={}){
    const timeoutMs=Number(options.timeoutMs||10000);
    const fetchOptions={...options};
    delete fetchOptions.timeoutMs;

    const controller=new AbortController();
    const timer=setTimeout(()=>controller.abort(),timeoutMs);

    try{
        const response=await fetch(API+path,{
            cache:"no-store",
            ...fetchOptions,
            signal:controller.signal
        });
        const text=await response.text();
        let data;
        try{
            data=JSON.parse(text);
        }catch{
            throw new Error(`Server returned HTTP ${response.status} with invalid JSON.`);
        }
        if(!response.ok){
            throw new Error(data.detail||data.error||`HTTP ${response.status}`);
        }
        return data;
    }catch(error){
        if(error?.name==="AbortError"){
            throw new Error("Fetch server status request timed out.");
        }
        throw error;
    }finally{
        clearTimeout(timer);
    }
}

async function localJson(action,payload={}){
    const response=await fetch(`/bonus-fetch.php?action=${encodeURIComponent(action)}`,{
        method:"POST",
        headers:{"Content-Type":"application/json"},
        body:JSON.stringify(payload),
        cache:"no-store"
    });
    const text=await response.text();
    let data;
    try{data=JSON.parse(text);}catch{throw new Error(`Website returned HTTP ${response.status} with invalid JSON.`);}
    if(!response.ok && !data.quotaExhausted) throw new Error(data.error||`HTTP ${response.status}`);
    return data;
}

const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));

async function releaseQueueSlot(){
    if(!currentQueueId) return;
    const id=currentQueueId;
    currentQueueId=null;
    try{
        await localJson("queue_leave",{queueId:id});
    }catch(error){
        console.warn("Could not release fetch queue slot",error);
    }
}

async function waitForQueueTurn(){
    const joined=await localJson("queue_join",{});
    currentQueueId=joined.queueId;
    if(!currentQueueId) throw new Error("Website queue did not return a queue ID.");

    let q=joined;
    while(!q.active){
        if(stopped) throw new Error("Fetch cancelled.");
        const position=Math.max(1,Number(q.position||1));
        setStatus(
            "Waiting in fetch queue…",
            `<strong>Queue position #${position}</strong>`,
            "Another user is fetching right now. Your fetch will start automatically when it is your turn.",
            1,
            "warn"
        );
        await sleep(1000);
        q=await localJson("queue_check",{queueId:currentQueueId});
        if(Number(q.position)===-1 && !q.active){
            throw new Error("Your queue slot expired. Please press Fetch again.");
        }
    }

    if(stopped) throw new Error("Fetch cancelled.");
    setStatus(
        "Your turn — starting fetch…",
        "<strong>Queue slot acquired.</strong>",
        "Preparing your fetch now.",
        2,
        "good"
    );
}

function updateQuotaLine(info){
    if(!quotaLine) return;

    const remaining = Math.max(
        0,
        Number(info?.remaining ?? 5)
    );

    quotaLine.classList.toggle(
        "exhausted",
        remaining <= 0
    );

    const word =
        remaining === 1
            ? "Fetch"
            : "Fetches";

    quotaLine.innerHTML =
        '⚡ <strong>' +
        remaining +
        ' ' +
        word +
        ' Available</strong>';
}

async function refreshQuota(){
    const phone=phoneInput.value.trim();

    if(!phone){
        updateQuotaLine({
            remaining:0
        });
        return;
    }

    try{
        updateQuotaLine(
            await localJson(
                "quota_status",
                {phone}
            )
        );
    }
    catch(e){
        console.warn(
            "Quota status failed",
            e
        );
    }
}

const FETCH_PURCHASE_PENDING_KEY =
    "bpl_pending_fetch_purchase_v1";


function makeFetchPurchaseRequestId(){
    if(
        window.crypto
        && typeof window.crypto.randomUUID==="function"
    ){
        return "web_"+window.crypto.randomUUID();
    }

    const randomPart=
        Math.random()
            .toString(36)
            .slice(2)
        +
        Math.random()
            .toString(36)
            .slice(2);

    return (
        "web_"
        + Date.now().toString(36)
        + "_"
        + randomPart
    );
}


function readPendingFetchPurchase(){
    try{
        const parsed=JSON.parse(
            localStorage.getItem(
                FETCH_PURCHASE_PENDING_KEY
            )
            || "null"
        );

        if(
            !parsed
            || typeof parsed!=="object"
        ){
            return null;
        }

        const packageId=
            String(
                parsed.package
                || ""
            );

        const requestId=
            String(
                parsed.requestId
                || ""
            );

        if(
            !["1","5","10","20"].includes(
                packageId
            )
            || requestId.length<12
        ){
            return null;
        }

        return {
            package:packageId,
            requestId
        };

    }catch(_){
        return null;
    }
}


function savePendingFetchPurchase(
    packageId,
    requestId
){
    try{
        localStorage.setItem(
            FETCH_PURCHASE_PENDING_KEY,
            JSON.stringify({
                package:String(packageId),
                requestId:String(requestId),
                createdAt:Date.now()
            })
        );
    }catch(_){}
}


function clearPendingFetchPurchase(){
    try{
        localStorage.removeItem(
            FETCH_PURCHASE_PENDING_KEY
        );
    }catch(_){}
}


function setFetchPurchaseStatus(
    message,
    type=""
){
    if(!fetchPurchaseStatus) return;

    fetchPurchaseStatus.textContent=
        String(message||"");

    fetchPurchaseStatus.classList.remove(
        "good",
        "bad"
    );

    if(type){
        fetchPurchaseStatus.classList.add(
            type
        );
    }
}


function setFetchPackageButtonsDisabled(
    disabled
){
    fetchPackageBtns.forEach(
        btn=>{
            btn.disabled=!!disabled;
        }
    );
}


function updateQuotaLineFromPurchase(
    quota
){
    if(!quotaLine) return;

    const remaining=Math.max(
        0,
        Number(
            quota?.remaining
            ?? 0
        )
    );

    quotaLine.classList.toggle(
        "exhausted",
        remaining<=0
    );

    const word=
        remaining===1
            ? "Fetch"
            : "Fetches";

    quotaLine.innerHTML=
        "⚡ <strong>"
        + remaining
        + " "
        + word
        + " Available</strong>";

    applyFetchAccessVisibility(
        remaining
    );
}


function openFetchShop(
    exhausted=false
){
    if(!limitModal) return;

    if(fetchShopIntro){
        fetchShopIntro.textContent=
            exhausted
                ? "Need fetches? Contact sinastry on Discord."
                : "Use your promoter credits to add fetches to your account.";
    }

    const title=$("limitTitle");

    if(title){
        title.textContent=
            exhausted
                ? "No Fetches Available"
                : "Buy Fetches";
    }

    setFetchPurchaseStatus("");

    limitModal.classList.remove(
        "hidden"
    );
}


async function buyFetchPackage(
    packageId
){
    packageId=String(
        packageId
        || ""
    );

    if(
        !["1","5","10","20"].includes(
            packageId
        )
    ){
        return;
    }

    let pending=
        readPendingFetchPurchase();

    if(
        pending
        && pending.package!==packageId
    ){
        setFetchPurchaseStatus(
            "A previous +"
            + pending.package
            + " purchase is waiting to finish. Tap that package again first.",
            "bad"
        );

        return;
    }

    if(!pending){
        pending={
            package:packageId,
            requestId:
                makeFetchPurchaseRequestId()
        };

        savePendingFetchPurchase(
            pending.package,
            pending.requestId
        );
    }

    setFetchPackageButtonsDisabled(
        true
    );

    setFetchPurchaseStatus(
        "Processing purchase…"
    );

    try{
        const response=await fetch(
            "/api/promoter.php",
            {
                method:"POST",
                credentials:"same-origin",
                headers:{
                    "Content-Type":
                        "application/json",
                    "Accept":
                        "application/json"
                },
                body:JSON.stringify({
                    action:"buy_fetches",
                    package:pending.package,
                    requestId:
                        pending.requestId
                })
            }
        );

        let data={};

        try{
            data=await response.json();
        }catch(_){}

        if(
            !response.ok
            || !data?.ok
        ){
            /*
             * A normal 4xx means the server gave
             * a definite answer, so this purchase
             * request can be discarded.
             *
             * 5xx/network errors keep the request ID
             * because the VPS may already have charged
             * it and a retry must use the same ID.
             */
            if(
                response.status>=400
                && response.status<500
                && response.status!==408
                && response.status!==429
            ){
                clearPendingFetchPurchase();
            }

            throw new Error(
                data?.error
                || data?.detail
                || "Purchase failed."
            );
        }

        clearPendingFetchPurchase();

        const quota=
            data?.quota
            || {};

        updateQuotaLineFromPurchase(
            quota
        );

        const added=Math.max(
            0,
            Number(
                quota?.added
                ?? data?.purchase?.fetches
                ?? packageId
            )
        );

        const remaining=Math.max(
            0,
            Number(
                quota?.remaining
                ?? 0
            )
        );

        setFetchPurchaseStatus(
            (
                added>0
                    ? "Added "
                        + added
                        + (
                            added===1
                                ? " fetch"
                                : " fetches"
                        )
                    : "Purchase already applied"
            )
            + ". "
            + remaining
            + (
                remaining===1
                    ? " fetch available."
                    : " fetches available."
            ),
            "good"
        );

    }catch(error){
        const message=
            String(
                error?.message
                || "Purchase failed."
            );

        /*
         * Network/server failure keeps the pending
         * request ID. Tapping the same package again
         * safely retries the exact same purchase.
         */
        const stillPending=
            readPendingFetchPurchase();

        setFetchPurchaseStatus(
            stillPending
                ? message
                    + " Tap the same package again to retry safely."
                : message,
            "bad"
        );

    }finally{
        setFetchPackageButtonsDisabled(
            false
        );
    }
}


function showLimitModal(){
    openFetchShop(true);
}

/*
 * Bonus Fetcher referral-link map.
 *
 * This uses the exact same data/domains.json file as the homepage,
 * so clicking Valid / Empty / Error cards opens the corresponding
 * Best Pokies List referral URL instead of the bare casino domain.
 */
let bonusFetcherReferlinks=new Map();
let bonusFetcherReferlinksReady=null;

function normalizeReferlinkDomain(value){
    let domain=String(value||"").trim().toLowerCase();
    domain=domain.replace(/^https?:\/\//i,"");
    domain=domain.split("/")[0];
    domain=domain.replace(/^www\./i,"");
    return domain;
}

async function loadBonusFetcherReferlinks(){
    try{
        const response=await fetch(
            "data/domains.json?t="+Date.now(),
            {cache:"no-store"}
        );

        if(!response.ok){
            throw new Error(
                `domains.json HTTP ${response.status}`
            );
        }

        const rows=await response.json();
        const map=new Map();

        if(Array.isArray(rows)){
            for(const item of rows){
                if(!item || typeof item!=="object") continue;

                const domain=
                    normalizeReferlinkDomain(item.domain);

                const url=
                    String(item.url||"").trim();

                if(
                    domain &&
                    /^https?:\/\//i.test(url)
                ){
                    /*
                     * Keep the first saved entry for a domain.
                     * This matches the main ordered domain list and avoids
                     * duplicate promotional entries unexpectedly replacing
                     * the primary referral URL.
                     */
                    if(!map.has(domain)){
                        map.set(domain,url);
                    }
                }
            }
        }

        bonusFetcherReferlinks=map;

        console.info(
            "Bonus Fetcher referral links loaded:",
            map.size
        );

        return map;

    }catch(error){
        console.warn(
            "Could not load Bonus Fetcher referral links:",
            error
        );

        return bonusFetcherReferlinks;
    }
}

function ensureBonusFetcherReferlinks(){
    if(!bonusFetcherReferlinksReady){
        bonusFetcherReferlinksReady=
            loadBonusFetcherReferlinks();
    }

    return bonusFetcherReferlinksReady;
}

function referlinkForDomain(domain){
    const d=normalizeReferlinkDomain(domain);

    if(!/^[a-z0-9.-]+$/.test(d)){
        return "";
    }

    return (
        bonusFetcherReferlinks.get(d)
        || `https://${d}/`
    );
}

async function openDomain(domain){
    const d=normalizeReferlinkDomain(domain);
    if(!/^[a-z0-9.-]+$/.test(d)) return;

    /*
     * Normally the map is already loaded. Waiting here also protects
     * clicks made immediately after page load.
     */
    await ensureBonusFetcherReferlinks();

    const url=referlinkForDomain(d);
    if(!url) return;

    window.open(
        url,
        "_blank",
        "noopener,noreferrer"
    );
}

ensureBonusFetcherReferlinks();

function makeClickable(el,domain){
    el.tabIndex=0;
    el.setAttribute("role","link");
    el.title=`Open ${domain} referral link in a new tab`;
    el.addEventListener("click",e=>{
        // The bonus expander lives inside the clickable domain card. Clicking
        // it must expand/collapse the bonuses instead of opening the domain.
        if(e.target?.closest?.(".otherBonuses, .wc-more, .wc-list, .community-badge, .bpl-meta")) return;
        openDomain(domain);
    });
    el.addEventListener("keydown",e=>{
        if(e.target?.closest?.(".otherBonuses, .wc-more, .wc-list, .community-badge, .bpl-meta")) return;
        if(e.key==="Enter"||e.key===" "){e.preventDefault();openDomain(domain);}
    });
}

function cleanBonusName(raw){
    let text=String(raw??"");
    text=text.replace(/<[^>]*>/g," ");
    const decoder=document.createElement("textarea");
    decoder.innerHTML=text;
    return (decoder.value||text).replace(/\s+/g," ").trim();
}

function cleanBackendBonus(bonus){
    /*
     * The VPS worker already decides which bonuses are valid. Keep that
     * decision intact and only normalise the returned fields for display.
     */
    if(!bonus || typeof bonus!=="object") return null;

    const amount=Number(
        bonus.amountValue ??
        bonus.amount
    );
    if(!Number.isFinite(amount) || amount<=0) return null;

    const minWithdrawal=
        bonus.minWithdrawal ??
        bonus.minWithdraw ??
        null;
    const maxWithdrawal=
        bonus.maxWithdrawal ??
        bonus.maxWithdraw ??
        null;

    return {
        name:cleanBonusName(bonus.name||""),
        amount,
        amountDisplay:
            bonus.amountDisplay ??
            bonus.amount ??
            amount,
        minWithdrawal,
        maxWithdrawal,
        turnover:
            Number.isFinite(Number(bonus.turnover))
                ? Number(bonus.turnover)
                : turnoverValue(amount,minWithdrawal),
        id:bonus.id ?? null
    };
}

let casinoIdentityOrder=[];

function resultIdentity(row){
    // 04 and 614 are different casino logins. Keep the exact digits.
    return String(row?.casinoIdentity||row?.credentialPhone||row?.phone||"").replace(/\D/g,"");
}

function resultStateKey(row){
    const domain=String(row?.domain||"").toLowerCase();
    const identity=resultIdentity(row);
    return identity?`${identity}|${domain}`:domain;
}

function collapseCasinoRows(valid,noData,errors){
    const rank={data:3,empty:2,error:1};
    const kept=new Map();
    const consider=(kind,row)=>{
        if(!row||typeof row!=="object") return;
        const identity=resultIdentity(row);
        if(identity) row.casinoIdentity=identity;
        const key=resultStateKey(row);
        const current=kept.get(key);
        if(current && rank[current.kind]>=rank[kind]) return;
        kept.set(key,{kind,row});
    };
    for(const row of valid||[]) consider("data",row);
    for(const row of noData||[]) consider("empty",row);
    for(const row of errors||[]) consider("error",row);
    const next={data:[],empty:[],error:[]};
    for(const item of kept.values()) next[item.kind].push(item.row);
    return next;
}

function accountNumber(row){
    const identity=resultIdentity(row);
    const index=casinoIdentityOrder.indexOf(identity);
    if(index>=0) return index+1;
    const extras=[...new Set([...rows,...emptyRows,...latestErrors].map(resultIdentity).filter(Boolean))]
        .filter(value=>!casinoIdentityOrder.includes(value))
        .sort();
    const extraIndex=extras.indexOf(identity);
    return extraIndex>=0?casinoIdentityOrder.length+extraIndex+1:0;
}

function accountBadgeText(row){
    const domain=String(row?.domain||"").toLowerCase();
    const identities=new Set();
    for(const item of [...rows,...emptyRows,...latestErrors]){
        if(String(item?.domain||"").toLowerCase()!==domain) continue;
        const identity=resultIdentity(item);
        if(identity) identities.add(identity);
    }
    if(identities.size<2) return "";
    const number=accountNumber(row);
    const digits=resultIdentity(row);
    const last4=digits.slice(-4);
    return number?`Account ${number} · ••••${last4}`:"";
}

function accountBadgeHtml(row){
    const text=accountBadgeText(row);
    return text?`<span class="accountBadge">${esc(text)}</span>`:"";
}

function rememberIdentityOrder(identities){
    const next=[];
    for(const item of (Array.isArray(identities)?identities:[])){
        const identity=resultIdentity(item);
        if(identity && !next.includes(identity)) next.push(identity);
    }
    if(next.length) casinoIdentityOrder=next;
}

async function loadCasinoIdentityOrder(){
    try{
        const response=await fetch("/api/account-phones.php",{
            credentials:"same-origin",
            cache:"no-store"
        });
        if(!response.ok) return;
        const data=await response.json();
        rememberIdentityOrder(data?.phones);
    }catch(_){}
}

function classifySuccess(domain,payload){
    const cashRaw=Number(payload?.cash ?? 0);
    const cash=Number.isFinite(cashRaw)?cashRaw:0;

    const cashWalletId=String(payload?.cashWalletId ?? "0");

    /*
     * Match the Tampermonkey wallet logic:
     * - wallet id 1 = free-credit wallet, so use its own withdraw limits
     * - otherwise use the account/global withdraw limits
     */
    const cashMinWithdrawal =
        cashWalletId==="1"
            ? (payload?.freeCreditMinWithdraw ?? null)
            : (payload?.globalMinWithdraw ?? payload?.minWithdraw ?? null);

    const cashMaxWithdrawal =
        cashWalletId==="1"
            ? (payload?.freeCreditMaxWithdraw ?? null)
            : (payload?.globalMaxWithdraw ?? payload?.maxWithdraw ?? null);

    const commission=cleanBackendBonus(payload?.commission);
    const share=cleanBackendBonus(payload?.share);
    const referral=cleanBackendBonus(payload?.referral);
    const newBonus=cleanBackendBonus(payload?.new);
    const rebate=cleanBackendBonus(payload?.rebate);

    // Every item in claimableBonuses has already passed the backend's
    // validity checks (ID, positive amount, allowed customBonus, min/max).
    // Keep the complete list, including bonuses also represented in the five
    // summary columns, so the expander really means ALL valid bonuses.
    const bonusSource=Array.isArray(payload?.claimableBonuses)
        ? payload.claimableBonuses
        : (Array.isArray(payload?.availableBonuses)?payload.availableBonuses:[]);
    const availableBonuses=bonusSource
        .map(cleanBackendBonus)
        .filter(Boolean);

    const row={
        domain,
        casinoIdentity:resultIdentity(payload),
        cash,
        cashMinWithdrawal,
        cashMaxWithdrawal,
        commission,
        share,
        referral,
        new:newBonus,
        rebate,
        availableBonuses
    };

    row.hasData = Boolean(
        row.cash>0 ||
        commission ||
        share ||
        referral ||
        newBonus ||
        rebate ||
        availableBonuses.length
    );
    return row;
}

function turnoverValue(amount,minWithdrawal){
    const a=Number(amount||0);
    const m=Number(minWithdrawal);
    if(!(a>0) || !Number.isFinite(m) || m<0) return null;
    return m/a;
}

function turnoverText(amount,minWithdrawal){
    const t=turnoverValue(amount,minWithdrawal);
    if(t===null) return "";
    const rounded=t>=10?t.toFixed(1):t.toFixed(2);
    return `${Number(rounded)}×`;
}

function bonusMiniHtml(label,bonus,extraClass=""){
    if(!bonus) return `<div class="cardBonus ${extraClass}"><span class="bonusLabel">${esc(label)}</span><span class="bonusNo">No</span></div>`;
    const turn=turnoverText(bonus.amount,bonus.minWithdrawal);
    return `<div class="cardBonus ${extraClass}">
        <span class="bonusLabel">${esc(label)}</span>
        <span class="bonusYes">Yes <span class="bonusAmount">(${esc(money(bonus.amount).replace("$",""))})</span></span>
        <span class="bonusWithdraw">Min ${esc(money(bonus.minWithdrawal).replace("$",""))}, Max ${esc(money(bonus.maxWithdrawal).replace("$",""))}${turn?` • <span class="turnoverTag">${esc(turn)}</span>`:""}</span>
    </div>`;
}

function rowBestTurnover(row){
    const values=[];
    for(const key of ["commission","share","referral","new","rebate"]){
        const b=row?.[key];
        if(!b) continue;
        const t=turnoverValue(b.amount,b.minWithdrawal);
        if(t!==null) values.push(t);
    }
    for(const b of (Array.isArray(row?.availableBonuses)?row.availableBonuses:[])){
        const t=turnoverValue(b.amount,b.minWithdrawal);
        if(t!==null) values.push(t);
    }
    return values.length?Math.min(...values):Number.POSITIVE_INFINITY;
}

function otherBonusesHtml(row){
    const all=Array.isArray(row?.availableBonuses)?row.availableBonuses:[];
    if(!all.length) return "";

    /*
     * The top five columns are only a summary. The expander is the complete
     * claimable-bonus list returned by syncData, including bonuses that may
     * also be represented by Com/Share/Ref/New/Rebate.
     */
    const sorted=[...all].sort((a,b)=>{
        const ta=turnoverValue(a.amount,a.minWithdrawal) ?? Number.POSITIVE_INFINITY;
        const tb=turnoverValue(b.amount,b.minWithdrawal) ?? Number.POSITIVE_INFINITY;
        return ta-tb || Number(b.amount||0)-Number(a.amount||0);
    });

    const turns=sorted
        .map(b=>turnoverValue(b.amount,b.minWithdrawal))
        .filter(v=>v!==null && Number.isFinite(v));
    const best=turns.length?Math.min(...turns):null;
    const bestText=best!==null
        ? `${Number((best>=10?best.toFixed(1):best.toFixed(2)))}× best`
        : "";

    return `<details class="otherBonuses">
        <summary>
            <span>🎁 All valid bonuses (${all.length})</span>
            <span class="otherBonusBest">${esc(bestText)} <span aria-hidden="true">⌄</span></span>
        </summary>
        <div class="otherBonusList">${
            sorted.map(b=>{
                const turn=turnoverText(b.amount,b.minWithdrawal);
                return `<div class="otherBonusRow">
                    <span class="otherBonusName">${esc(b.name||"Available bonus")}</span>
                    <span class="otherBonusAmount">${esc(
                        (typeof b.amountDisplay==="string" && b.amountDisplay.includes("-"))
                            ? `$${b.amountDisplay}`
                            : money(b.amount)
                    )}</span>
                    <span class="otherBonusMeta">
                        ${turn?`<span class="turnoverGood">${esc(turn)} turnover</span>`:""}
                        <span>Min ${b.minWithdrawal==null?"—":esc(money(b.minWithdrawal))}</span>
                        <span>Max ${b.maxWithdrawal==null?"—":esc(money(b.maxWithdrawal))}</span>
                    </span>
                </div>`;
            }).join("")
        }</div>
    </details>`;
}

let fetcherOrdered=null;
let fetcherRevealNext=false;
let fetcherScrollNext=false;
let fetcherJobRevealed=false;

function sortRows(){
    const entries=[
        ...rows.map(item=>({item,kind:"data"})),
        ...[...emptyRows].map(item=>({item,kind:"empty"})),
        ...[...latestErrors].map(item=>({item,kind:"error"}))
    ];
    if(typeof window.bplOrderFetcherCards==="function"){
        const ordered=window.bplOrderFetcherCards(entries.map((entry,index)=>({
            domain:entry.item?.domain||"",
            casinoIdentity:typeof resultIdentity==="function"?resultIdentity(entry.item):"",
            walletRow:walletRowFromResult(entry.item,entry.kind),
            index,
            entry
        })));
        fetcherOrdered=ordered.map(card=>card.entry).filter(Boolean);
    }else{
        const tie=(a,b)=>String(a.domain||"").localeCompare(String(b.domain||""))||accountNumber(a)-accountNumber(b);
        fetcherOrdered=[
            ...[...rows].sort(tie).map(item=>({item,kind:"data"})),
            ...[...emptyRows].sort(tie).map(item=>({item,kind:"empty"})),
            ...[...latestErrors].sort(tie).map(item=>({item,kind:"error"}))
        ];
    }
    displayRows=fetcherOrdered.filter(entry=>entry.kind==="data").map(entry=>entry.item);
    currentPage=1;
    renderPage();
}

window.bplRefreshFetcherOrder=function(){
    /* The homepage list is the only card list, and it already applies Sort / Filter. */
};


let lastFetchDiff={
    byDomain:{},
    counts:{},
    totalChanged:0
};

function resetFetchDiff(){
    lastFetchDiff={
        byDomain:{},
        counts:{},
        totalChanged:0
    };
    if(changeSummary){
        changeSummary.classList.add("hidden");
    }
    if(changeSummaryItems){
        changeSummaryItems.innerHTML="";
    }
}

function numericEqual(a,b){
    const aa=Number(a||0), bb=Number(b||0);
    return Math.abs(aa-bb)<0.005;
}

function bonusComparable(bonus){
    if(!bonus) return null;
    return {
        amount:Number(bonus.amount||0),
        minWithdrawal:bonus.minWithdrawal ?? bonus.minWithdraw ?? null,
        maxWithdrawal:bonus.maxWithdrawal ?? bonus.maxWithdraw ?? null,
        name:String(bonus.name||"").replace(/<[^>]*>/g," ").replace(/\s+/g," ").trim()
    };
}

function stateMapFromCache(cache){
    const map={};

    for(const row of (Array.isArray(cache?.rows)?cache.rows:[])){
        map[resultStateKey(row)]={kind:"data",row};
    }
    for(const row of (Array.isArray(cache?.emptyRows)?cache.emptyRows:[])){
        map[resultStateKey(row)]={kind:"empty",row};
    }
    for(const err of (Array.isArray(cache?.errors)?cache.errors:[])){
        map[resultStateKey(err)]={kind:"error",error:err};
    }

    return map;
}

function currentStateMap(){
    return stateMapFromCache({
        rows,
        emptyRows,
        errors:latestErrors
    });
}

function addDomainChange(domain,label,tone="info",kind="changed"){
    const key=String(domain||"");
    const stored=key.includes("|")?key:key.toLowerCase();
    if(!stored) return;

    if(!lastFetchDiff.byDomain[stored]){
        lastFetchDiff.byDomain[stored]=[];
    }

    const duplicate=lastFetchDiff.byDomain[stored].some(
        x=>x.label===label
    );
    if(duplicate) return;

    lastFetchDiff.byDomain[stored].push({label,tone,kind});
    lastFetchDiff.counts[kind]=(lastFetchDiff.counts[kind]||0)+1;
}

function compareBonusType(domain,label,key,oldRow,newRow){
    const oldBonus=bonusComparable(oldRow?.[key]);
    const newBonus=bonusComparable(newRow?.[key]);

    if(!oldBonus && newBonus){
        addDomainChange(domain,`NEW ${label.toUpperCase()} ${money(newBonus.amount)}`,"good","newBonus");
        return;
    }

    if(oldBonus && !newBonus){
        addDomainChange(domain,`${label.toUpperCase()} REMOVED`,"bad","bonusRemoved");
        return;
    }

    if(!oldBonus || !newBonus) return;

    if(!numericEqual(oldBonus.amount,newBonus.amount)){
        const delta=newBonus.amount-oldBonus.amount;
        addDomainChange(
            domain,
            `${label.toUpperCase()} ${delta>0?"+":""}${money(delta)}`,
            delta>0?"good":"warn",
            "bonusChanged"
        );
    }else if(
        String(oldBonus.minWithdrawal)!==String(newBonus.minWithdrawal) ||
        String(oldBonus.maxWithdrawal)!==String(newBonus.maxWithdrawal)
    ){
        addDomainChange(domain,`${label.toUpperCase()} LIMITS CHANGED`,"purple","bonusChanged");
    }
}

function computeFetchDiff(previous){
    resetFetchDiff();

    if(!previous || !previous.savedAt){
        return;
    }

    const oldMap=stateMapFromCache(previous);
    const newMap=currentStateMap();
    const domains=new Set([
        ...Object.keys(oldMap),
        ...Object.keys(newMap)
    ]);

    for(const domain of domains){
        const oldState=oldMap[domain];
        const newState=newMap[domain];

        if(!newState) continue;

        if(!oldState){
            if(newState.kind==="data"){
                addDomainChange(domain,"NEW RESULT","good","newResult");
            }else if(newState.kind==="error"){
                addDomainChange(domain,"NEW ERROR","bad","newError");
            }
            continue;
        }

        if(oldState.kind==="error" && newState.kind!=="error"){
            addDomainChange(domain,"ERROR FIXED","good","errorFixed");
        }

        if(oldState.kind!=="error" && newState.kind==="error"){
            addDomainChange(domain,"NEW ERROR","bad","newError");
            continue;
        }

        if(oldState.kind==="data" && newState.kind==="empty"){
            addDomainChange(domain,"NOW EMPTY","bad","nowEmpty");
            continue;
        }

        if(oldState.kind==="empty" && newState.kind==="data"){
            const newCash=Number(newState.row?.cash||0);
            if(newCash>0){
                addDomainChange(domain,`BALANCE FOUND ${money(newCash)}`,"good","balanceChanged");
            }else{
                addDomainChange(domain,"NEW BONUS","good","newBonus");
            }
        }

        if(newState.kind==="data"){
            const oldRow=oldState.row||{};
            const newRow=newState.row||{};

            if(oldState.kind==="data"){
                const oldCash=Number(oldRow.cash||0);
                const newCash=Number(newRow.cash||0);

                if(!numericEqual(oldCash,newCash)){
                    const delta=newCash-oldCash;
                    addDomainChange(
                        domain,
                        `BALANCE ${delta>0?"+":""}${money(delta)}`,
                        delta>0?"good":"warn",
                        "balanceChanged"
                    );
                }
            }

            compareBonusType(domain,"Com","commission",oldRow,newRow);
            compareBonusType(domain,"Share","share",oldRow,newRow);
            compareBonusType(domain,"Ref","referral",oldRow,newRow);
            compareBonusType(domain,"New","new",oldRow,newRow);
            compareBonusType(domain,"Rebate","rebate",oldRow,newRow);
        }

        if(oldState.kind==="error" && newState.kind==="error"){
            const oldType=String(oldState.error?.type||"");
            const newType=String(newState.error?.type||"");
            if(oldType!==newType){
                addDomainChange(domain,`ERROR: ${newType.toUpperCase()}`,"bad","newError");
            }
        }
    }

    lastFetchDiff.totalChanged=Object.keys(lastFetchDiff.byDomain).length;
    renderFetchDiffSummary();
}

function diffLookupKey(value){
    const key=String(value||"");
    return key.includes("|")?key:key.toLowerCase();
}

function diffBadgesHtml(domain){
    const items=lastFetchDiff.byDomain[diffLookupKey(domain)]||[];
    if(!items.length) return "";
    return `<div class="changeBadges">${
        items.slice(0,4).map(
            x=>`<span class="changeBadge ${esc(x.tone)}">${esc(x.label)}</span>`
        ).join("")
    }</div>`;
}

function domainHasDiff(domain){
    return Boolean(
        lastFetchDiff.byDomain[diffLookupKey(domain)]?.length
    );
}

function renderFetchDiffSummary(){
    /* Diff data stays in memory. The old summary panel is not part of the result list. */
    changeSummary?.classList.add("hidden");
}

function bonusSig(bonus){
    if(!bonus) return "";
    return [bonus.amount,bonus.minWithdrawal,bonus.maxWithdrawal,bonus.name||""].join(",");
}

function cardSignature(item,kind){
    const key=resultStateKey(item);
    const badge=accountBadgeText(item);
    const diff=(lastFetchDiff.byDomain[diffLookupKey(key)]||[]).map(x=>x.label).join(",");
    const bonusFocus=typeof bonusDisplayMode==="undefined"?"none":bonusDisplayMode;
    if(kind==="error") return ["error",item?.type||"",item?.reason||"",item?.httpCode||"",badge,diff,bonusFocus].join("|");
    if(kind==="empty") return ["empty",badge,diff,bonusFocus].join("|");
    const extras=(Array.isArray(item?.availableBonuses)?item.availableBonuses:[]).map(bonusSig).join(";");
    return [
        "data",
        Number(item?.cash)||0,
        item?.cashMinWithdrawal??"",
        item?.cashMaxWithdrawal??"",
        bonusSig(item?.commission),
        bonusSig(item?.share),
        bonusSig(item?.referral),
        bonusSig(item?.new),
        bonusSig(item?.rebate),
        extras,
        badge,
        diff,
        bonusFocus
    ].join("|");
}

function homepageRecord(domain){
    if(typeof allDomains==="undefined"||!Array.isArray(allDomains)||typeof normalizeDomain!=="function") return null;
    const key=normalizeDomain(domain);
    return allDomains.find(item=>normalizeDomain(item.domain)===key)||null;
}

function fetcherFavicon(img,domainKey){
    const api=typeof INTEL_API==="string"?INTEL_API:"https://intel.bestpokieslist.online";
    const candidates=[
        "api/favicon.php?domain="+encodeURIComponent(domainKey),
        api+"/favicon/"+encodeURIComponent(domainKey)
    ];
    if(typeof letterIcon==="function") candidates.push(letterIcon(domainKey));
    let index=0;
    const load=()=>{
        if(index>=candidates.length){
            img.onerror=null;
            return;
        }
        img.src=candidates[index++];
    };
    img.className="icon";
    img.alt="";
    img.referrerPolicy="no-referrer";
    img.decoding="async";
    img.onerror=load;
    load();
}

function walletRowFromResult(item,kind){
    if(kind==="error"){
        return {
            domain:item?.domain||"",
            casinoIdentity:resultIdentity(item),
            fetchState:"error",
            errorType:item?.type||"",
            errorReason:item?.reason||""
        };
    }
    return item;
}

function emptyBonusPanel(){
    const panel=walletEl("span","wc-panel");
    const grid=walletEl("span","wc-grid");
    for(const col of WALLET_BONUS_COLUMNS){
        const cell=walletEl("span","wc-cell wc-"+col.key);
        cell.append(walletEl("span","wc-label",col.label),walletEl("span","wc-none","—"));
        grid.append(cell);
    }
    panel.append(grid);
    return panel;
}

/* Homepage wallet card: favicon, live, members, balance, five rounded bonus tiles. */
function fillResultCard(card,item,kind){
    const domainKey=typeof normalizeDomain==="function"?normalizeDomain(item?.domain||""):String(item?.domain||"").toLowerCase();
    const record=homepageRecord(domainKey);
    const openList=card.querySelector(".wc-list") && card.querySelector(".wc-list").hidden===false;
    const icon=document.createElement("img");
    fetcherFavicon(icon,domainKey);

    const info=document.createElement("span");
    info.className="domain-info";
    const badgeText=accountBadgeText(item);
    if(badgeText){
        const badge=document.createElement("span");
        badge.className="casino-account-badge";
        badge.textContent=badgeText;
        info.append(badge);
    }
    const domain=document.createElement("span");
    domain.className="domain";
    domain.textContent=item?.domain||"—";
    const status=document.createElement("span");
    status.className="domain-status";
    status.dataset.statusDomain=domainKey;
    if(typeof applyDomainStatus==="function") applyDomainStatus(status,domainKey);
    const meta=document.createElement("span");
    meta.className="domain-meta";
    meta.append(status);
    const communityCount=Number(record?.community||0);
    if(communityCount>0){
        const members=document.createElement("span");
        members.className="community-badge";
        const word=communityCount===1?"member":"members";
        members.innerHTML=`👥 <b>${communityCount}</b><span class="community-badge-word"> ${word}</span>`;
        meta.append(members);
    }
    const added=document.createElement("span");
    added.className="domain-added";
    const addedText=record&&typeof relativeAddedAt==="function"?relativeAddedAt(record.addedAt):"";
    added.textContent=addedText||"";
    if(!addedText) added.hidden=true;

    const walletRow=walletRowFromResult(item,kind);
    info.append(domain,meta,added,walletBalanceBlock(walletRow));
    const panel=kind==="error"?null:(walletBonusPanel(walletRow)||emptyBonusPanel());
    card.className="card wallet-card"+(domainHasDiff(resultStateKey(item))?" changed":"");
    card.replaceChildren(icon,info);
    if(panel) card.append(panel);
    if(openList){
        const list=card.querySelector(".wc-list");
        const toggle=card.querySelector(".wc-more");
        if(list){
            list.hidden=false;
            toggle?.classList.add("open");
            toggle?.setAttribute("aria-expanded","true");
        }
    }
    if(typeof window.bplDecorateCard==="function"){
        window.bplDecorateCard(card,{
            domain:item?.domain||"",
            casinoIdentity:resultIdentity(item)
        });
    }
}

/* Fetch rows paint onto the homepage cards. The fetcher panel does not draw a second list. */
function renderFetcherCards(){
    resultsBody.replaceChildren();
    resultsPanel.classList.add("hidden");
    noDataPanel.classList.add("hidden");
    errorsPanel.classList.add("hidden");
    const sortEmpty=$("fetcherSortEmpty");
    if(sortEmpty) sortEmpty.hidden=true;

    const reveal=fetcherRevealNext;
    const scroll=fetcherScrollNext;
    fetcherRevealNext=false;
    fetcherScrollNext=false;
    if(!rows.length && !emptyRows.length && !latestErrors.length) return;
    if(typeof window.bplApplyFetcherRows!=="function") return;

    const parts=[];
    if(rows.length) parts.push(`${rows.length} with bonuses`);
    if(emptyRows.length) parts.push(`${emptyRows.length} empty`);
    if(latestErrors.length) parts.push(`${latestErrors.length} to fix`);
    window.bplApplyFetcherRows({
        rows,
        emptyRows,
        errors:latestErrors,
        identities:casinoIdentityOrder,
        reveal,
        scroll,
        note:parts.join(" · ")
    });
}

function renderPage(){
    renderFetcherCards();
}

function renderNoData(){
    renderFetcherCards();
}

function renderErrors(){
    renderFetcherCards();
}


let latestErrors=[];

function showCachedTimestamp(){
    if(!cacheStamp) return;
    cacheStamp.classList.add("hidden");
    cacheStamp.textContent="";
}

function setQuietStatus(text){
    setStatus(text,"","",100,"good");
    statusPanel.classList.add("is-quiet");
    finishLine.classList.add("hidden");
    finishLine.innerHTML="";
}


async function getPreviousLastFetch(phone,password){
    try{
        return await localJson("last_fetch_get",{phone,password});
    }catch(_){
        return null;
    }
}

async function saveLastFetch(phone,password){
    try{
        await localJson("last_fetch_save",{
            phone,
            password,
            rows,
            emptyRows,
            errors:latestErrors
        });
    }catch(error){
        console.warn("Could not save last fetch",error);
    }
}

async function viewLastFetch(){
    const phone=phoneInput.value.trim();
    const password=passwordInput.value;

    if(!phone||!password){
        setStatus(
            "Credentials required",
            "<strong>Enter your phone number and password to view your saved fetch.</strong>",
            "Viewing saved results does not use one of your fetches.",
            0,
            "warn"
        );
        return;
    }

    lastFetchBtn.disabled=true;
    try{
        const cached=await localJson("last_fetch_get",{phone,password});
        rememberIdentityOrder(cached.identities);
        await loadCasinoIdentityOrder();
        const collapsed=collapseCasinoRows(cached.rows,cached.emptyRows,cached.errors);
        rows=collapsed.data;
        emptyRows=collapsed.empty;
        latestErrors=collapsed.error;
        currentPage=1;
        resetFetchDiff();

        fetcherRevealNext=true;
        fetcherScrollNext=true;
        sortRows();
        renderNoData();
        renderErrors([...latestErrors]);
        showCachedTimestamp(cached.savedAt);
        setQuietStatus("Results ready");
    }catch(error){
        setStatus(
            "No saved fetch loaded",
            `<strong>${esc(error.message||error)}</strong>`,
            "Run a fetch once and the completed results will be saved automatically.",
            0,
            "warn"
        );
    }finally{
        lastFetchBtn.disabled=false;
    }
}

let resultsRefreshAgain=false;

function refreshResultsInBackground(){
    /* One download at a time. A rescue that finishes during that download is applied immediately after, not dropped. */
    if(resultsRefreshPromise){
        resultsRefreshAgain=true;
        return resultsRefreshPromise;
    }
    const run=()=>{
        resultsRefreshPromise=loadAndRenderResults()
            .catch(error=>{
                console.warn("Background result refresh failed",error);
            })
            .finally(()=>{
                resultsRefreshPromise=null;
                if(resultsRefreshAgain){
                    resultsRefreshAgain=false;
                    run();
                }
            });
        return resultsRefreshPromise;
    };
    return run();
}

async function loadAndRenderResults(){
    if(!currentJobId) return;
    const data=await apiJson(`/jobs/${encodeURIComponent(currentJobId)}/results`);
    const all=data.results||{};
    const valid=[], noData=[], errors=[];
    await loadCasinoIdentityOrder();
    for(const [storedKey,result] of Object.entries(all)){
        const domain=String(result?.domain||"").toLowerCase()||(String(storedKey).includes("|")?String(storedKey).split("|").slice(1).join("|"):String(storedKey));
        const status=String(result?.status||"");
        const casinoIdentity=resultIdentity(result);
        if(status==="success"){
            const row=classifySuccess(domain,result);
            if(row.hasData) valid.push(row); else noData.push(row);
        }else if(status==="invalid_login"){
            errors.push({domain,casinoIdentity,type:"invalid_login",reason:result.message||"Invalid Login.",httpCode:result.http||""});
        }else if(status==="captcha"){
            errors.push({domain,casinoIdentity,type:"captcha",reason:result.message||"Invalid Captcha!",httpCode:result.http||""});
        }else if(status==="permanent"){
            errors.push({domain,casinoIdentity,type:"permanent",reason:result.message||"Permanent endpoint error",httpCode:result.http||""});
        }else if(status==="network_failed"){
            errors.push({domain,casinoIdentity,type:"network",reason:"Temporary network / connection failure",httpCode:""});
        }else{
            errors.push({domain,casinoIdentity,type:status||"other",reason:result.message||"Other fetch error",httpCode:result.http||""});
        }
    }
    const collapsed=collapseCasinoRows(valid,noData,errors);
    rows=collapsed.data;
    emptyRows=collapsed.empty;
    latestErrors=collapsed.error;
    if(!fetcherJobRevealed){
        fetcherRevealNext=true;
        fetcherJobRevealed=true;
    }
    sortRows();
    renderNoData();
    renderErrors(latestErrors);
}

async function tryWebsiteLoginBeforeFetch(
    phone,
    password
){
    try{
        const response=await fetch(
            "/api/login.php",
            {
                method:"POST",
                credentials:"same-origin",
                cache:"no-store",
                headers:{
                    "Content-Type":"application/json",
                    "Accept":"application/json"
                },
                body:JSON.stringify({
                    phone,
                    password
                })
            }
        );

        let data={};

        try{
            data=await response.json();
        }catch(_){}

        return {
            ok:
                response.ok
                && data?.ok===true,

            created:
                data?.created===true,

            migrated:
                data?.migrated===true,

            loggedIn:
                data?.loggedIn===true,

            status:
                Number(response.status||0),

            error:
                String(
                    data?.error
                    || data?.detail
                    || ""
                )
        };

    }catch(error){
        return {
            ok:false,
            status:0,
            error:String(
                error?.message
                || "Login request failed."
            )
        };
    }
}


async function run(){
    const phone=phoneInput.value.trim(), password=passwordInput.value;

    if(phone && password){
        saveCredentialsLocally();
    }

    let previousLastFetchPromise=
        Promise.resolve(null);

    if(!phone||!password){
        setStatus(
            "Credentials required",
            "<strong>Enter your phone number and password before fetching.</strong>",
            "Use the same credentials you normally use across your pokie sites.",
            0,
            "warn"
        );
        return;
    }

    /*
     * FIRST CLICK WHEN LOGGED OUT:
     *
     * Only restore/create the Best Pokies List website
     * session. Do NOT authorize/consume a fetch yet.
     */
    if(!fetchLoginConfirmed){
        runBtn.disabled=true;

        try{
            setStatus(
                "Signing in…",
                "<strong>Logging into your Best Pokies List account.</strong>",
                "This does not use a fetch.",
                20
            );

            const websiteLogin=
                await tryWebsiteLoginBeforeFetch(
                    phone,
                    password
                );

            /*
             * /api/login.php is now authoritative.
             *
             * Existing accounts are checked against the local
             * password hash.
             *
             * Brand-new accounts are first verified by login-only
             * requests across the domain fleet. The website account
             * is created only after a real domain accepts the
             * credentials.
             */
            if(
                !websiteLogin.ok
                || !websiteLogin.loggedIn
            ){
                throw new Error(
                    websiteLogin.error
                    || "Could not sign in to your website account."
                );
            }

            fetchLoginConfirmed=true;
            updateRunButtonLabel();
            window.dispatchEvent(new CustomEvent("bpl:fetcher-login"));

            /*
             * Refresh entitlement separately.
             *
             * Creating/logging into the website account does NOT
             * consume a fetch.
             */
            try{
                await refreshQuota();
            }catch(_){}

            if(websiteLogin.created){
                setStatus(
                    "Account created",
                    "<strong>Your Best Pokies List account is ready.</strong>",
                    "Your login was verified successfully. New accounts start with 0 fetches. Need fetches? Contact sinastry on Discord.",
                    100,
                    "good"
                );

                return;
            }

            setStatus(
                "Logged in",
                "<strong>You're signed in to Best Pokies List.</strong>",
                "Press Fetch Bonus Data again to run a fetch. Logging in did not use a fetch.",
                100,
                "good"
            );

            return;

        }catch(error){
            fetchLoginConfirmed=false;
            updateRunButtonLabel();

            setStatus(
                "Login failed",
                "<strong>"
                    + esc(
                        error?.message
                        || error
                    )
                    + "</strong>",
                "Check your phone number and password. No fetch was used.",
                100,
                "bad"
            );

            return;

        }finally{
            runBtn.disabled=false;
        }
    }

    /*
     * Disable only the Fetch button during preflight.
     *
     * Do NOT show Starting fetch / Force Stop yet because
     * we have not confirmed that a fetch is allowed.
     */
    runBtn.disabled=true;

    try{
        /*
         * The account was already authenticated either
         * from the PHP session or by the previous button
         * press. Do not perform another login here.
         */
        const websiteLogin={
            ok:true,
            status:200,
            error:""
        };

        /*
         * Check/consume allowance only AFTER the login
         * restoration attempt.
         */
        const quota=
            await localJson(
                "authorize_fetch",
                {phone}
            );

        updateQuotaLine(quota);

        if(
            quota.quotaExhausted
            || quota.allowed===false
        ){
            applyFetchAccessVisibility(0);

            /*
             * If this is an existing account and login
             * succeeded, they can now safely open the
             * promoter-credit purchase shop.
             */
            if(websiteLogin.ok){
                openFetchShop(true);
            }else{
                /*
                 * At zero quota a failed website login means
                 * opening the shop would only lead to
                 * "Login required", so explain the credential
                 * problem instead.
                 */
                const message=
                    websiteLogin.error
                    || "Could not sign in to your website account.";

                setStatus(
                    "Sign in required",
                    "<strong>"
                        + esc(message)
                        + "</strong>",
                    "Check your phone number and password. Signing in does not use a fetch.",
                    0,
                    "warn"
                );
            }

            return;
        }

        /*
         * We now KNOW a real fetch was authorized.
         * Only from this point should fetch-running UI appear.
         */
        stopBtn.disabled=false;
        stopBtn.classList.remove("hidden");
        stopBtn.parentElement.classList.add("hasStop");

        stopped=false;
        rows=[];
        emptyRows=[];
        displayRows=[];
        currentPage=1;
        currentJobId=null;
        fetcherJobRevealed=false;
        currentQueueId=null;
        lastRescueRecovered=-1;
        fetchStarted=performance.now();

        resultsPanel.classList.add("hidden");
        noDataPanel.classList.add("hidden");
        errorsPanel.classList.add("hidden");

        finishLine.classList.add("hidden");
        finishLine.innerHTML="";

        showCachedTimestamp(0);
        resetFetchDiff();

        setStatus(
            "Starting fetch…",
            "<strong>Preparing your fetch.</strong>",
            "If another fetch is already running, you will automatically be placed in the queue.",
            1
        );

        if(stopped){
            throw new Error(
                "Fetch cancelled."
            );
        }

        
        // Every browser submits immediately and the worker returns the real
        // queue position. This prevents separate phones/tabs from bypassing
        // a browser-side queue.
        const start=await apiJson("/jobs",{
            method:"POST",
            headers:{"Content-Type":"application/json"},
            body:JSON.stringify({phone,password,requestId:quota.requestId||"",accountId:quota.fetchAuth?.accountId||"",deviceId:quota.fetchAuth?.deviceId||"",sessionHash:quota.fetchAuth?.sessionHash||"",authTimestamp:Number(quota.fetchAuth?.authTimestamp||0),authSignature:quota.fetchAuth?.authSignature||"",fallbacks:collectFallbackCredentials()})
        });
        updateQuotaLine(start.quota||quota);

        currentJobId=start.jobId;
        if(!currentJobId) throw new Error("Fetch server did not return a job ID.");

        // Load the previous result in the background. This must never delay
        
        previousLastFetchPromise=getPreviousLastFetch(phone,password);

        const initialPosition=Number(start.queuePosition||0);
        if(String(start.state||"")==="queued"){
            setStatus(
                initialPosition>0 ? `Queued — position ${initialPosition}` : "Queued",
                "<strong>Your fetch is waiting for the current fetch to finish.</strong>",
                initialPosition>0
                    ? "You can leave this screen open — your fetch will start automatically when it reaches the front."
                    : "Waiting for a confirmed queue position.",
                1,
                "warn"
            );
        }

        let mainRendered=false;
        let activeFetchStarted=null;
        let consecutivePollErrors=0;
        let lastRescueRenderAt=0;
        let lastQueuePosition=null;
        let lastState=null;

        for(;;){
            if(stopped && !currentJobId) throw new Error("Fetch cancelled.");

            let st;
            try{
                st=await apiJson(
                    `/jobs/${encodeURIComponent(currentJobId)}`,
                    {timeoutMs:8000}
                );
                consecutivePollErrors=0;
            }catch(error){
                consecutivePollErrors++;
                const message=String(error?.message||"");
                if(/job not found|HTTP 404/i.test(message)){
                    throw new Error("This fetch is no longer running. Retry.");
                }

                // The job keeps running on the server even when one status
                // check is slow or a phone briefly loses signal. Keep polling
                // (about four minutes of consecutive failures) instead of
                // reporting a failed fetch that actually completes.
                if(consecutivePollErrors<=60){
                    if(consecutivePollErrors>=3){
                        setQuietStatus("Reconnecting to fetch server... your fetch is still running");
                    }
                    await sleep(Math.min(5000,400*consecutivePollErrors));
                    continue;
                }

                throw new Error("Lost connection to the fetch server. Your fetch may still finish - tap Last fetch in a minute to load it.");
            }

            const total=Number(st.total||648), done=Number(st.finished||0), left=Math.max(0,total-done);
            const pct=total?Math.min(st.mainComplete?99:98,(done/total)*98):2;
            const unresolved=Array.isArray(st.unresolved)?st.unresolved.length:0;

            if(st.state==="queued"){
                const position=Number(st.queuePosition||0);
                if(position>0){
                    const ahead=Math.max(0,position-1);
                    const moved=lastQueuePosition!==null && position<lastQueuePosition;
                    setStatus(
                        `Queued — position ${position}`,
                        `<strong>${ahead===0 ? "You are next in line." : `${ahead} fetch${ahead===1 ? "" : "es"} ahead of you.`}</strong>`,
                        `${moved ? "Queue moved forward • " : ""}Starts automatically when you reach the front.`,
                        1,
                        "warn"
                    );
                    lastQueuePosition=position;
                }else{
                    setStatus(
                        "Queued",
                        "<strong>Waiting for a confirmed queue position.</strong>",
                        "Completed, stopped and failed fetches are not counted.",
                        1,
                        "warn"
                    );
                }
            }else if(st.state==="stopping"){
                setStatus(
                    "Stopping fetch…",
                    "<strong>Stop requested.</strong>",
                    "A queued fetch is skipped. A running fetch stops without starting another one.",
                    99,
                    "warn"
                );
            }else if((st.state==="main"||st.state==="starting") && !st.mainComplete){
                if(activeFetchStarted===null) activeFetchStarted=performance.now();

                const accountPasses=Math.max(1,Number(st.accountPasses||1));
                const accountPass=Math.max(1,Number(st.accountPass||1));
                const elapsed=Math.max(0.01,Number((accountPasses>1?st.passElapsed:st.elapsed)||st.elapsed||0));
                const rate=done>0 ? done/elapsed : 0;
                const recent=Array.isArray(st.recent) && st.recent.length
                    ? st.recent[st.recent.length-1]
                    : null;
                const recentText=recent && recent.domain
                    ? ` • latest: ${esc(recent.domain)}${recent.kind ? ` (${esc(recent.kind)})` : ""}`
                    : "";

                setStatus(
                    st.state==="starting"
                        ? "Starting fetch…"
                        : (accountPasses>1
                            ? `Running — account ${accountPass} of ${accountPasses}`
                            : "Running — fetching bonuses"),
                    `<strong>${done} domains processed • ${left} domains left • ${rate.toFixed(1)}/s</strong>`,
                    `${Number(st.syncSuccess||0)} successful • ${Number(st.invalidLogin||0)} invalid login • ${Number(st.captcha||0)} CAPTCHA • ${Number(st.networkFailed||0)} network • ${Number(st.retries||0)} retries • ${elapsed.toFixed(1)}s${recentText}`,
                    pct
                );
            }else if(st.state==="rescue" || ((st.state==="main"||st.state==="starting") && st.mainComplete)){
                const recovered=Number(st.rescueRecovered||0);

                if(!mainRendered){
                    refreshResultsInBackground();
                    mainRendered=true;
                    lastRescueRecovered=recovered;
                }

                setQuietStatus(unresolved>0 ? `Results ready · checking ${unresolved} unresolved…` : "Results ready");

                if(recovered!==lastRescueRecovered){
                    lastRescueRecovered=recovered;
                    refreshResultsInBackground();
                }
            }else if(st.state==="complete"||st.state==="stopped"){
                if(resultsRefreshPromise) await resultsRefreshPromise;
                await loadAndRenderResults();
                if(st.state==="complete"){
                    const previousLastFetch=await previousLastFetchPromise;
                    computeFetchDiff(previousLastFetch);
                    sortRows();
                    renderNoData();
                    renderErrors([...latestErrors]);
                    await saveLastFetch(phone,password);
                    window.dispatchEvent(new CustomEvent("bpl:fetcher-saved",{detail:{withData:rows.length,empty:emptyRows.length,errors:latestErrors.length}}));
                }
                setQuietStatus(st.state==="stopped" ? "Fetch stopped" : "Results ready");
                break;
            }else if(st.state==="failed"){
                throw new Error("The fetch service reported an error.");
            }

            lastState=st.state;

            // Keep status movement visibly live. The request is tiny JSON, so a
            // 4-5 Hz poll while active is cheap compared with the actual fetch.
            // Queue polling is slightly slower because queue position changes less often.
            await sleep(
                st.state==="queued"
                    ? POLL_QUEUE_MS
                    : (st.state==="rescue" ? POLL_RESCUE_MS : POLL_ACTIVE_MS)
            );
        }
    }catch(error){
        
        // occurs, request a stop before freeing the global queue slot so the
        // next queued user does not overlap this abandoned job.
        if(currentJobId && !stopped){
            try{await apiJson(`/jobs/${encodeURIComponent(currentJobId)}/stop`,{method:"POST"});}catch(_){}
        }

        if(stopped || String(error?.message||"")==="Fetch cancelled."){
            setStatus("Fetch stopped","<strong>Your fetch was cancelled.</strong>","The next queued user can start now.",100,"warn");
        }else{
            setStatus("Fetch failed",`<strong>${esc(error.message||error)}</strong>`,"You can try again.",100,"bad");
            console.error(error);
        }
    }finally{
        currentJobId=null;
        currentQueueId=null;
        runBtn.disabled=false; stopBtn.disabled=false; stopBtn.classList.add("hidden"); stopBtn.parentElement.classList.remove("hasStop");
    }
}

lastFetchBtn.addEventListener("click",()=>{
    if (typeof setFetcherCollapsed === "function") setFetcherCollapsed(false);
    viewLastFetch();
});

stopBtn.addEventListener("click",async()=>{
    if(stopped) return;
    stopped=true;stopBtn.disabled=true;stopBtn.textContent="Stopping…";
    try{
        if(currentJobId){
            await apiJson(`/jobs/${encodeURIComponent(currentJobId)}/stop`,{method:"POST"});
            setStatus(
                "Stopping fetch…",
                "<strong>Stop requested.</strong>",
                "If you were queued, your request will be skipped. If your fetch was active, it will stop as quickly as possible.",
                99,
                "warn"
            );
        }else if(currentQueueId){
            // Legacy local queue state from older page versions.
            await releaseQueueSlot();
            setStatus("Queue cancelled","<strong>You left the old website queue.</strong>","No fetch was started.",100,"warn");
        }
    }catch(e){console.error(e);}finally{stopBtn.textContent="Force Stop";}
});


sortSelect?.addEventListener("change",sortRows);
prevBtn.addEventListener("click",()=>{if(currentPage>1){currentPage--;renderPage();}});
nextBtn.addEventListener("click",()=>{const max=Math.ceil(displayRows.length/PAGE_SIZE);if(currentPage<max){currentPage++;renderPage();}});

openPageBtn?.addEventListener("click",async()=>{
    const start=(currentPage-1)*PAGE_SIZE;
    const pageRows=displayRows.slice(start,start+PAGE_SIZE);
    if(!pageRows.length) return;

    const domains=[];
    for(const row of pageRows){
        const domain=String(row?.domain||"")
            .trim()
            .toLowerCase()
            .replace(/^www\./,"");
        if(/^[a-z0-9.-]+$/.test(domain) && !domains.includes(domain)) domains.push(domain);
    }
    const pageDomains=domains.slice(0,25);
    if(!pageDomains.length) return;

    // Open the tabs inside the click so the browser treats them as the
    // user's gesture. Destinations are filled in after the server grant.
    let opened=0;
    let blocked=0;
    const tabs=[];
    for(const domain of pageDomains){
        const tab=window.open("about:blank","_blank");
        if(tab){
            opened++;
            try{tab.opener=null;}catch(_){}
            tabs.push({domain,tab});
        }else{
            blocked++;
        }
    }

    const closeTabs=()=>{
        for(const item of tabs){
            try{item.tab.close();}catch(_){}
        }
    };
    let opensByDomain=null;
    let directFallback=false;
    try{
        const response=await fetch("/api/bulk-open.php",{
            method:"POST",
            credentials:"same-origin",
            headers:{"Content-Type":"application/json","Accept":"application/json"},
            body:JSON.stringify({domains:pageDomains})
        });
        const data=await response.json().catch(()=>null);
        if(response.ok && data && data.ok===true && Array.isArray(data.opens)){
            opensByDomain=new Map();
            for(const item of data.opens){
                const domain=String(item&&item.domain||"");
                const url=String(item&&item.url||"");
                if(/^[a-z0-9.-]+$/.test(domain) && url.startsWith("/api/bulk-open.php?")){
                    opensByDomain.set(domain,url);
                }
            }
        }else if(response.status===401 || response.status===429){
            // Logged out, or this account is past the short grant limit.
            // Direct casino URLs are not referral clicks and are not exemptions.
            directFallback=true;
        }else{
            closeTabs();
            alert("Could not open this page of sites. Please try again.");
            return;
        }
    }catch(error){
        directFallback=true;
    }

    for(const item of tabs){
        const granted=opensByDomain?opensByDomain.get(item.domain):"";
        const target=directFallback?`https://${item.domain}/`:(granted||"");
        if(!target){
            try{item.tab.close();}catch(_){}
            opened=Math.max(0,opened-1);
            continue;
        }
        try{
            item.tab.location.replace(target);
        }catch(_){
            try{item.tab.location.href=target;}catch(__){}
        }
    }

    if(blocked>0){
        alert(
            `Opened ${opened} site${opened===1?"":"s"}. ` +
            `${blocked} tab${blocked===1?" was":"s were"} blocked by your browser.\n\n` +
            `Allow pop-ups and redirects for ${location.hostname}, then press Open Page again.`
        );
    }
});
togglePassword.addEventListener("click",()=>{const showing=passwordInput.type==="text";passwordInput.type=showing?"password":"text";togglePassword.textContent=showing?"Show":"Hide";});
advancedCredsBtn.addEventListener("click",()=>{
    const opening=advancedCreds.classList.contains("hidden");
    advancedCreds.classList.toggle("hidden",!opening);
    advancedCredsBtn.textContent=opening
        ? "Fallbacks ▴"
        : "Fallbacks ▾";
});

[
    fallbackPhone2,fallbackPassword2,
    fallbackPhone3,fallbackPassword3,
    fallbackPhone4,fallbackPassword4
].forEach(el=>{
    el.addEventListener("change",saveCredentialsLocally);
    el.addEventListener("blur",saveCredentialsLocally);
});

phoneInput.addEventListener("input",()=>{
    fetchLoginConfirmed=false;
    updateRunButtonLabel();
});
phoneInput.addEventListener("blur",()=>{restorePasswordForPhone();saveCredentialsLocally();refreshQuota();});
phoneInput.addEventListener("change",()=>{restorePasswordForPhone();saveCredentialsLocally();refreshQuota();});
passwordInput.addEventListener("change",saveCredentialsLocally);
passwordInput.addEventListener("blur",saveCredentialsLocally);
restoreCredentialsLocally();
if(phoneInput.value.trim()) refreshQuota();
buyFetchesBtn?.addEventListener(
    "click",
    ()=>{
        openFetchShop(false);
    }
);

if(
    cfg.openShop
){
    setTimeout(
        ()=>{
            openFetchShop(
                displayedFetchCount()===0
            );
        },
        100
    );
}


fetchPackageBtns.forEach(
    btn=>{
        btn.addEventListener(
            "click",
            ()=>{
                buyFetchPackage(
                    btn.dataset.fetchPackage
                );
            }
        );
    }
);

closeLimitModal?.addEventListener("click",()=>limitModal.classList.add("hidden"));
limitModal?.addEventListener("click",e=>{if(e.target===limitModal) limitModal.classList.add("hidden");});
runBtn.addEventListener("click",async()=>{
    window.dispatchEvent(new CustomEvent("bpl:fetcher-busy",{detail:true}));
    try{ await run(); }finally{ window.dispatchEvent(new CustomEvent("bpl:fetcher-busy",{detail:false})); }
});
};

/* Show or hide Bonus Fetcher result cards only. The directory stays put. */
(function(){
    const KEY = "bplFetcherResultsHidden";
    let hidden = false;
    try { hidden = sessionStorage.getItem(KEY) === "1"; } catch (error) {}

    function panels() {
        return ["resultsPanel", "noDataPanel", "errorsPanel"]
            .map(id => document.getElementById(id))
            .filter(Boolean);
    }
    function hasResults() {
        return panels().some(panel => !panel.classList.contains("hidden") && panel.querySelector(".card.wallet-card, .domainCard, .noDataCard, .errorCard"));
    }
    function ensureButton() {
        let button = document.getElementById("bplHideFetchResults");
        if (button) return button;
        button = document.createElement("button");
        button.id = "bplHideFetchResults";
        button.type = "button";
        button.className = "bf-results-toggle";
        const meta = document.querySelector("#bplFetcher .bf-meta");
        (meta || document.getElementById("bplFetcher"))?.append(button);
        button.addEventListener("click", () => setHidden(!hidden));
        return button;
    }
    function apply() {
        const live = hasResults();
        const button = ensureButton();
        button.hidden = !live;
        button.textContent = hidden ? "Show results" : "Hide results";
        button.setAttribute("aria-expanded", hidden ? "false" : "true");
        document.getElementById("bplFetcher")?.classList.toggle("bf-results-hidden", hidden && live);
        document.getElementById("bplFetcherExtras")?.classList.toggle("bf-results-hidden", hidden && live);
    }
    function setHidden(next) {
        hidden = Boolean(next);
        try { sessionStorage.setItem(KEY, hidden ? "1" : "0"); } catch (error) {}
        apply();
    }
    if ("MutationObserver" in window) {
        const observer = new MutationObserver(() => apply());
        panels().forEach(panel => observer.observe(panel, {childList: true, attributes: true, attributeFilter: ["class"], subtree: true}));
    }
    apply();
})();
