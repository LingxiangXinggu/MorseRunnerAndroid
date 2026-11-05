// SPDX-License-Identifier: MPL-2.0
#include "../core/engine.hpp"
#include <cassert>
#include <fstream>
#include <iostream>
#include <numeric>
using namespace mr;
static void advance(Engine& e,int n){for(int i=0;i<n;i++){auto a=e.audio();assert(a.size()==Block);for(float x:a){assert(std::isfinite(x));assert(std::abs(x)<=20001);}}}
int main(int argc,char** argv){
    assert(!Config{}.qsk);
    // Explicit receiver speeds must be independent of our TX speed in every mode.
    for(Mode mode:{Mode::Pileup,Mode::Single,Mode::Wpx,Mode::Hst}){
        Config c;c.mode=mode;c.wpm=10;c.dxWpmMin=12;c.dxWpmMax=23;
        Engine independent(c,{"K1ABC","W1AW","BD6IUT"},91);
        bool low=false,high=false;
        for(int i=0;i<128;i++){
            auto& remote=independent.addCaller();
            assert(remote.wpm>=12&&remote.wpm<=23&&remote.oper.wpm==remote.wpm);
            low|=remote.wpm==12;high|=remote.wpm==23;
            Qrm interference(independent);assert(interference.wpm>=12&&interference.wpm<=23);
        }
        assert(low&&high);
        independent.tune(120,500,0);assert(independent.me->wpm==120);
        assert(independent.addCaller().wpm<=23);
        for(int fixed:{5,25,120}){
            c.dxWpmMin=c.dxWpmMax=fixed;
            Engine slowTx(c,{"K1ABC"},47);c.wpm=110;Engine fastTx(c,{"K1ABC"},47);c.wpm=10;
            auto& slow=slowTx.addCaller();auto& fast=fastTx.addCaller();
            assert(slow.wpm==fixed&&fast.wpm==fixed);
            slow.morse("PARIS");fast.morse("PARIS");
            assert(slow.envelope.size()==fast.envelope.size());
            assert(slow.envelope.size()==slowTx.keyer.envelope("PARIS",fixed).size());
            slow.oper.state=fast.oper.state=OperatorState::NeedQso;
            assert(slow.oper.delay()==fast.oper.delay());
            assert(slow.oper.replyTimeout()==fast.oper.replyTimeout());
        }
    }
    for(auto bounds:{std::pair<int,int>{4,20},{20,121},{30,10},{0,30},{20,0}}){
        Config bad;bad.dxWpmMin=bounds.first;bad.dxWpmMax=bounds.second;bool rejected=false;
        try{Engine invalid(bad,{"K1ABC"},1);}catch(const std::invalid_argument&){rejected=true;}
        assert(rejected);
    }
    std::cout<<"PASS: independent TX/remote speeds, all four modes, fixed and random ranges, QRM, HST timing, emitted envelopes, invalid ranges\n";
    Keyer k;assert(k.encode("PARIS")==".--. .- .-. .. ...~");
    assert(k.encode("CQ N0CALL TEST")=="-.-. --.-  -. ----- -.-. .- .-.. .-..  - . ... -~");
    for(int speed=5;speed<=120;speed++){auto a=k.envelope("CQ TEST N0CALL 5NN001 ? /",speed);assert(!a.empty()&&a.size()%Block==0);for(float x:a)assert(x>=0&&x<=1);}
    auto calls=Engine::loadCalls("# comment\r\n bd6iut \r\nBD6IUT\nK1ABC\ninvalid call\n");assert(calls.size()==2&&calls[0]=="BD6IUT");
    assert(Engine::matchCall("BD6IUT","BD6IUT")==Match::Yes);
    assert(Engine::matchCall("BD6?","BD6IUT")==Match::Almost);
    assert(Engine::matchCall("?","BD6IUT")==Match::No);
    assert(Engine::matchCall("BD6IUX","BD6IUT")==Match::Almost);
    assert(Engine::matchCall("K1ABC","BD6IUT")==Match::No);
    assert(Engine::prefix("BD6IUT")=="BD6");assert(Engine::prefix("EA8/K1ABC")=="K1");assert(Engine::prefix("3D2CR")=="3D2");
    Qso q;Engine::check(q);assert(q.error=="NIL");q.trueCall="K1ABC";q.rst=q.trueRst=599;q.nr=q.trueNr=7;Engine::check(q);assert(q.error.empty());q.dupe=true;Engine::check(q);assert(q.error=="DUP");q.dupe=false;q.nr=8;Engine::check(q);assert(q.error=="NR");
    Config cfg;cfg.mode=Mode::Single;cfg.wpm=40;Engine e(cfg,{"K1ABC"},42);advance(e,100);
    Dx* dx=nullptr;for(auto& s:e.stations)if((dx=dynamic_cast<Dx*>(s.get())))break;assert(dx);assert(dx->myCall=="K1ABC");
    // Full source-state exchange: CQ -> call -> received his-call+NR -> reply -> TU -> verified log.
    e.me->hisCall=dx->myCall;dx->oper.set(OperatorState::NeedQso);dx->oper.received(bit(Message::HisCall)|bit(Message::NR));
    if(dx->oper.state==OperatorState::NeedNr)dx->oper.received(bit(Message::NR));
    assert(dx->oper.state==OperatorState::NeedEnd);
    int nr=dx->nr,rst=dx->rst;dx->oper.received(bit(Message::TU));assert(dx->oper.state==OperatorState::Done);
    assert(e.save("K1ABC",rst,nr));assert(e.log.back().error.empty());auto stats=e.stats();assert(stats.points==1&&stats.mult==1&&stats.score==1);
    // Early save must receive truth after TU finishes, like Contest.pas + Log.pas.
    Engine early(cfg,{"W1AW"},4);auto& d=early.addCaller();d.oper.set(OperatorState::NeedEnd);assert(early.save("W1AW",d.rst,d.nr));assert(early.log.back().error=="NIL");d.oper.received(bit(Message::TU));early.collectTruth();assert(early.log.back().error.empty());
    Config noise;noise.mode=Mode::Wpx;noise.duration=1;noise.activity=7;Engine noisy(noise,{"K1ABC","BD6IUT","JA1ABC","DL1ABC"},87);noisy.send(Message::CQ,"");advance(noisy,1350);assert(!noisy.running);
    Config hst;hst.mode=Mode::Hst;Engine he(hst,{"K1ABC","W1AW","JA1ABC","DL1ABC","BD6IUT"},9);advance(he,20);assert(he.dxCount()==4);assert(!he.cfg.qsb&&he.cfg.bandwidth==600);
    bool threw=false;try{Engine bad(cfg,{},1);}catch(const std::invalid_argument&){threw=true;}assert(threw);
    // End-to-end test through the audio clock and Enter Sends Messages, without forcing operator state.
    Engine complete(cfg,{"K1ABC"},42);
    auto remote=[&]() -> Dx* {for(auto& station:complete.stations){auto* d=dynamic_cast<Dx*>(station.get());if(d&&!d->dead&&d->myCall=="K1ABC")return d;}return nullptr;};
    auto waitFor=[&](auto predicate){for(int i=0;i<1000;i++){if(predicate())return true;complete.audio();}return false;};
    assert(waitFor([&]{auto* d=remote();return d&&d->state==State::Listening;}));
    int trueNr=remote()->nr,trueRst=remote()->rst;
    assert(!complete.enter("K1ABC",trueRst,0));
    assert(waitFor([&]{auto* d=remote();return d&&d->oper.state==OperatorState::NeedEnd&&d->state==State::Listening&&complete.me->state==State::Listening;}));
    assert(complete.enter("K1ABC",trueRst,trueNr));assert(complete.log.size()==1);
    assert(waitFor([&]{return complete.log.back().error.empty();}));
    assert(complete.stats().score==1);
    if(argc>1){std::ifstream f(argv[1],std::ios::binary);std::string text((std::istreambuf_iterator<char>(f)),{});assert(Engine::loadCalls(text).size()==50263);}
    std::cout<<"PASS: keyer 5..120 WPM, SCP, matching, prefixes, scoring, late verification, all-effects 60s session, HST, finite bounded audio, audio-clock ESM QSO\n";
}
