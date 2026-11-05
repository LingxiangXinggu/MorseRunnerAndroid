// SPDX-License-Identifier: MPL-2.0
// Derived from BH1SCW/MorseRunner 1.71 Pascal units; see docs/PORTING.md.
#include "engine.hpp"
#include <cctype>
#include <iomanip>
#include <regex>
#include <sstream>
#include <stdexcept>

namespace mr {
static std::string serial(int n){std::ostringstream s;s<<std::setfill('0')<<std::setw(3)<<n;return s.str();}
static bool among(OperatorState s,std::initializer_list<OperatorState> a){return std::find(a.begin(),a.end(),s)!=a.end();}
using OS=OperatorState;using M=Message;

Engine::Engine(Config c,std::vector<std::string> list,uint32_t seed):cfg(std::move(c)),rng(seed),calls(std::move(list)){
    cfg.wpm=std::clamp(cfg.wpm,10,120);cfg.activity=std::clamp(cfg.activity,1,10);cfg.duration=std::clamp(cfg.duration,1,60);
    if(cfg.dxWpmMin||cfg.dxWpmMax){
        if(cfg.dxWpmMin<5||cfg.dxWpmMax>120||cfg.dxWpmMin>cfg.dxWpmMax)throw std::invalid_argument("Invalid remote WPM range");
    }
    cfg.pitch=std::clamp(cfg.pitch,300,1000);cfg.bandwidth=std::clamp(cfg.bandwidth,100,1000);
    if(calls.empty())throw std::invalid_argument("No callsigns in selected database");
    if(cfg.mode==Mode::Wpx){cfg.qrn=cfg.qrm=cfg.qsb=cfg.flutter=cfg.lids=true;}
    if(cfg.mode==Mode::Hst){cfg.qrn=cfg.qrm=cfg.qsb=cfg.flutter=cfg.lids=false;cfg.activity=4;cfg.bandwidth=600;}
    filter.configure(rnd(0.7*Rate/cfg.bandwidth),float(10*log10(500.0/cfg.bandwidth)));alternate=filter;
    me=std::make_unique<Me>(*this);
}
std::vector<std::string> Engine::loadCalls(const std::string& text){
    std::set<std::string> out;std::istringstream in(text);std::string line;
    while(std::getline(in,line)){
        auto first=line.find_first_not_of(" \t\r\n");if(first==std::string::npos||line[first]=='#')continue;
        line=line.substr(first,line.find_last_not_of(" \t\r\n")-first+1);
        if(line.size()<3||line.size()>24)continue;
        for(auto& c:line)c=char(std::toupper(static_cast<unsigned char>(c)));
        if(std::all_of(line.begin(),line.end(),[](char c){return (c>='A'&&c<='Z')||(c>='0'&&c<='9')||c=='/';}))out.insert(line);
    }return {out.begin(),out.end()};
}
std::string Engine::pickCall(){if(calls.empty())return "P29SX";int i=rng.integer(int(calls.size()));std::string s=calls[i];if(cfg.mode==Mode::Hst)calls.erase(calls.begin()+i);return s;}
Dx& Engine::addCaller(){stations.push_back(std::make_unique<Dx>(*this));return *static_cast<Dx*>(stations.back().get());}
int Engine::dxCount()const{int n=0;for(const auto& s:stations){auto* d=dynamic_cast<Dx*>(s.get());if(d&&!d->dead&&d->oper.state!=OS::Done)n++;}return n;}
void Engine::cleanup(){stations.erase(std::remove_if(stations.begin(),stations.end(),[](const auto& s){return s->dead;}),stations.end());}
void Engine::finished(){
    if(cfg.mode!=Mode::Single&&cfg.mode!=Mode::Hst&&(has(me->messages,M::CQ)||(!log.empty()&&has(me->messages,M::TU)&&has(me->messages,M::MyCall)))){
        int n=rng.poisson(cfg.activity/2.0);for(int i=0;i<n;i++)addCaller();
    }
    for(int i=int(stations.size())-1;i>=0;i--)if(!stations[i]->dead)stations[i]->event(Event::MeFinished);
}
void Engine::started(){for(int i=int(stations.size())-1;i>=0;i--)if(!stations[i]->dead)stations[i]->event(Event::MeStarted);}
void Engine::send(M m,const std::string& call){
    if(!running)return;
    if(m==M::HisCall){if(!call.empty())me->hisCall=call;callSent=true;}
    if(m==M::NR)nrSent=true;
    me->send(m);
}
void Engine::abort(){if(!running)return;if(has(me->messages,M::HisCall))callSent=false;if(has(me->messages,M::NR))nrSent=false;me->abort();}
void Engine::updateCall(const std::string& call){if(me->hisCall!=call){callSent=false;if(me->updateCall(call))callSent=true;}}
bool Engine::enter(const std::string& call,int rst,int nr,bool saveOnly){
    if(!running)return false;
    if(saveOnly)return save(call,rst,nr);
    if(call.empty()){send(M::CQ,call);return false;}
    bool c=callSent,n=nrSent,r=nr>0;
    if(!c||(!n&&!r))send(M::HisCall,call);
    if(!n)send(M::NR,call);
    if(n&&!r)send(M::Qm,call);
    if(r&&(c||n)){send(M::TU,call);return save(call,rst,nr);}
    mustAdvance=true;return false;
}
void Engine::check(Qso& q){q.error=q.trueCall.empty()?"NIL":q.dupe?"DUP":q.trueRst!=q.rst?"RST":q.trueNr!=q.nr?"NR":"";}
bool Engine::save(const std::string& call,int rst,int nr){
    if(!running||call.size()<3||rst<100||rst>999||nr<1||nr>99999)return false;
    Qso q;q.time=elapsed();q.call=call;replace(q.call,"?","");q.rst=rst;q.nr=nr;q.sentNr=me->nr;q.prefix=prefix(q.call);
    for(const auto& old:log)if(old.call==q.call&&old.error.empty())q.dupe=true;
    log.push_back(q);collectTruth();check(log.back());callSent=nrSent=false;me->nr++;return true;
}
void Engine::collectTruth(){
    if(log.empty())return;
    auto& q=log.back();
    for(int i=int(stations.size())-1;i>=0;i--){
        auto* d=dynamic_cast<Dx*>(stations[i].get());
        if(d&&!d->dead&&d->oper.state==OS::Done&&d->myCall==q.call){q.trueCall=d->myCall;q.trueNr=d->nr;q.trueRst=d->rst;d->dead=true;check(q);break;}
    }
}
std::string Engine::prefix(const std::string& call){
    static const std::regex callsign("(([0-9][A-Z])|([A-Z]{1,2}))[0-9][A-Z0-9]*[A-Z]");
    static const std::regex pfx("(([0-9][A-Z])|([A-Z]{1,2}))[0-9]");std::smatch m;
    if(!std::regex_search(call,m,callsign))return "-";
    if(m.position()>0&&call[size_t(m.position()-1)]!='/')return "-";
    std::string actual=m.str();return std::regex_search(actual,m,pfx)?m.str():"-";
}
int Engine::callScore(const std::string& call){Keyer k;int score=-1;for(char c:k.encode(call))score+=c=='.'?2:c=='-'?4:c==' '?2:0;return score;}
Stats Engine::stats()const{
    Stats s;std::set<std::string> raw,valid;double window=std::min(300.0,elapsed());int recent=0;
    for(const auto& q:log){s.rawPoints++;raw.insert(q.prefix);if(q.time>elapsed()-window)recent++;
        if(cfg.mode==Mode::Hst){int value=callScore(q.call);s.rawScore+=value;if(q.error.empty())s.score+=value;}
        if(q.error.empty()){s.points++;valid.insert(q.prefix);}
    }
    s.rawMult=int(raw.size());s.mult=int(valid.size());if(cfg.mode!=Mode::Hst){s.rawScore=s.rawPoints*s.rawMult;s.score=s.points*s.mult;}
    s.rate=window>0?rnd(recent*3600/window):0;return s;
}
void Engine::tune(int wpm,int bandwidth,int rit){
    cfg.wpm=std::clamp(wpm,10,120);me->wpm=cfg.wpm;cfg.rit=std::clamp(rit,-500,500);
    if(cfg.mode!=Mode::Hst&&cfg.bandwidth!=bandwidth){cfg.bandwidth=std::clamp(bandwidth,100,1000);filter.configure(rnd(0.7*Rate/cfg.bandwidth),float(10*log10(500.0/cfg.bandwidth)));alternate=filter;}
}
Samples Engine::audio(){
    Samples re(Block),im(Block);if(!running)return re;
    blockNumber++;if(blockNumber<6)return re;
    for(int i=0;i<Block;i++){re[i]=float(18000*(rng.unit()-0.5));im[i]=float(18000*(rng.unit()-0.5));}
    if(cfg.qrn){for(auto& v:re)if(rng.unit()<0.01)v=float(360000*(rng.unit()-0.5));if(rng.unit()<0.01)stations.push_back(std::make_unique<Qrn>(*this));}
    if(cfg.qrm&&rng.unit()<0.0002)stations.push_back(std::make_unique<Qrm>(*this));
    for(auto& s:stations)if(!s->dead&&s->state==State::Sending){
        Samples a=s->block();for(int i=0;i<int(a.size());i++){float b=s->bfo()-ritPhase-float(i*TwoPi*cfg.rit/Rate);re[i]+=a[i]*cosf(b);im[i]-=a[i]*sinf(b);}
    }
    ritPhase=float(std::remainder(ritPhase+Block*TwoPi*cfg.rit/Rate,TwoPi));
    if(me->state==State::Sending){
        auto a=me->block();float gain=powf(10,cfg.monitorDb/20),rf=1;
        for(int i=0;i<int(a.size());i++){
            if(cfg.qsk){float target=1-a[i]/me->amplitude;rf=rf>target?target:rf*0.997f+0.003f;re[i]=gain*a[i]+rf*re[i];im[i]=gain*a[i]+rf*im[i];}
            else{re[i]=gain*a[i];im[i]=gain*a[i];}
        }
    }
    Samples altRe=re,altIm=im;alternate.apply(altRe,altIm);filter.apply(re,im);
    if(blockNumber%10==0){std::swap(filter,alternate);alternate.reset();}
    int period=rnd(double(Rate)/cfg.pitch);Samples out(Block);
    for(int i=0;i<Block;i++){double p=TwoPi*carrierSample/period;out[i]=re[i]*float(sin(p))-im[i]*float(cos(p));carrierSample=(carrierSample+1)%period;}
    agc.apply(out);
    me->tick();for(int i=int(stations.size())-1;i>=0;i--)if(!stations[i]->dead)stations[i]->tick();
    collectTruth();cleanup();
    if(cfg.mode==Mode::Single&&dxCount()==0){me->messages=bit(M::CQ);addCaller().event(Event::MeFinished);}
    else if(cfg.mode==Mode::Hst){int n=cfg.activity-dxCount();if(n>0){me->messages=bit(M::CQ);for(int i=0;i<n;i++)addCaller().event(Event::MeFinished);}}
    if(elapsed()>=cfg.duration*60)running=false;
    return out;
}

float Station::bfo(){float out=phase;phase+=float(TwoPi*pitch/Rate);if(phase>TwoPi)phase-=float(TwoPi);return out;}
Samples Station::block(){
    int count=std::min(Block,int(envelope.size())-sendPos);if(count<=0){envelope.clear();return {};}
    Samples out(envelope.begin()+sendPos,envelope.begin()+sendPos+count);sendPos+=Block;if(sendPos>=int(envelope.size()))envelope.clear();return out;
}
void Station::tick(){
    if(state==State::Sending&&envelope.empty()){msgText.clear();state=State::Listening;event(Event::Sent);}
    else if(state!=State::Sending){if(timeout>-1)timeout--;if(timeout==0)event(Event::Timeout);}
}
void Station::send(M m){
    if(envelope.empty())messages=0;
    if(m==M::None){state=State::Listening;return;}
    messages|=bit(m);
    static const char* texts[]={"","CQ <my> TEST","<#>","TU","<my>","<his>","QSO B4","?","NIL","","R <#>","R <#> <#>","DE <my>","DE <my> <my>","DE <my> <#>","DE <my> <my> <#>","NR?","CQ CQ TEST <my> <my> TEST","<my> <my> <#>","QRL?","QRL?   QRL?","<his>  QSY QSY","AGN"};
    text(texts[static_cast<unsigned>(m)]);
}
void Station::text(std::string s){
    if(s.find("<#>")!=std::string::npos){replace(s,"<#>",numberText(),false);replace(s,"<#>",numberText());}
    replace(s,"<my>",myCall);
    if(!msgText.empty())msgText+=' ';
    msgText+=s;morse(msgText);
}
void Station::morse(const std::string& text){
    if(envelope.empty()){sendPos=0;phase=0;}
    envelope=e.keyer.envelope(text,wpm);for(auto& v:envelope)v*=amplitude;state=State::Sending;timeout=Never;
}
std::string Station::numberText(){
    std::string out=std::to_string(rst)+serial(nr);
    if(nrError){int i=int(out.size())-1;if(out[i]<'2'||out[i]>'7')i--;if(out[i]>='2'&&out[i]<='7'){out[i]+=e.rng.unit()<0.5?-1:1;out+="EEEEE "+serial(nr);}nrError=false;}
    replace(out,"599","5NN");
    if(e.cfg.mode!=Mode::Hst){replace(out,"000","TTT");replace(out,"00","TT");if(e.rng.unit()<0.4)replace(out,"0","O");else if(e.rng.unit()<0.97)replace(out,"0","T");if(e.rng.unit()<0.97)replace(out,"9","N");}
    return out;
}
Me::Me(Engine& e):Station(e){myCall=e.cfg.call;wpm=e.cfg.wpm;amplitude=300000;pitch=e.cfg.pitch;}
void Me::event(Event ev){if(ev==Event::Sent)e.finished();}
void Me::abort(){envelope.clear();messages=bit(M::Garbage);msgText.clear();pieces.clear();state=State::Listening;event(Event::Sent);}
void Me::text(std::string s){
    size_t at;
    while((at=s.find("<his>"))!=std::string::npos){if(at>0)pieces.push_back(s.substr(0,at));pieces.push_back("@");s.erase(0,at+5);}
    if(!s.empty())pieces.push_back(s);
    if(state!=State::Sending&&!pieces.empty()){next();e.started();}
}
void Me::next(){msgText.clear();Station::text(pieces.front()=="@"?hisCall:pieces.front());}
Samples Me::block(){auto out=Station::block();if(envelope.empty()&&!pieces.empty()){pieces.erase(pieces.begin());if(!pieces.empty())next();if(e.mustAdvance){e.advance=true;e.mustAdvance=false;}}return out;}
bool Me::updateCall(const std::string& call){
    if(call.empty())return false;
    if(!pieces.empty()&&pieces.front()=="@"){
        auto next=e.keyer.envelope(call,wpm);for(auto& v:next)v*=amplitude;
        if(int(next.size())>=sendPos&&int(envelope.size())>=sendPos&&std::equal(envelope.begin(),envelope.begin()+sendPos,next.begin())){envelope=std::move(next);hisCall=call;return true;}
    }
    for(size_t i=1;i<pieces.size();i++)if(pieces[i]=="@"){hisCall=call;return true;}
    return false;
}

Match Engine::matchCall(const std::string& c,const std::string& actual,bool lids){
    std::vector<std::vector<int>> matrix(c.size()+1,std::vector<int>(actual.size()+1));
    for(size_t x=1;x<=c.size();x++)matrix[x][0]=matrix[x-1][0]+2;
    for(size_t x=1;x<=c.size();x++)for(size_t y=1;y<=actual.size();y++){
        int t=matrix[x][y-1];if(x<c.size()&&c[x-1]!='?')t+=2;
        int l=matrix[x-1][y]+(c[x-1]!='?'?2:0);
        int d=matrix[x-1][y-1]+((c[x-1]==actual[y-1]||c[x-1]=='?')?0:2);
        matrix[x][y]=std::min({t,l,d});
    }
    int penalty=matrix.back().back();Match result=penalty==0?Match::Yes:penalty<=2?Match::Almost:Match::No;
    if(!lids&&c.size()==2&&result==Match::Almost)result=Match::No;
    if(result==Match::Yes&&(c.size()!=actual.size()||c.find('?')!=std::string::npos))result=Match::Almost;
    if(std::count_if(c.begin(),c.end(),[](char x){return x!='?';})<2)result=Match::No;
    return result;
}
Match Operator::isMyCall(){
    auto result=Engine::matchCall(e.me->hisCall,call,e.cfg.lids);
    if(e.cfg.lids&&e.me->hisCall.size()>3){if(result==Match::Yes&&e.rng.unit()<0.01)result=Match::Almost;else if(result==Match::Almost&&e.rng.unit()<0.04)result=Match::Yes;}return result;
}
void Operator::set(OS s){state=s;patience=s==OS::NeedQso?rnd(e.rng.rayleigh(4)):5;repeat=s==OS::NeedQso&&e.cfg.mode!=Mode::Single&&e.cfg.mode!=Mode::Hst&&e.rng.unit()<0.1?2:1;}
void Operator::dec(){if(state==OS::Done)return;if(--patience<1)state=OS::Failed;}
int Operator::delay(){if(state==OS::NeedPrevEnd)return Never;if(e.cfg.mode==Mode::Hst)return blocks(0.05+0.5*e.rng.unit()*10/(e.cfg.dxWpmMax?wpm:e.cfg.wpm));return blocks(0.1+0.5*e.rng.unit());}
int Operator::replyTimeout(){double v=e.cfg.mode==Mode::Hst?blocks(60.0/(e.cfg.dxWpmMax?wpm:e.cfg.wpm)):blocks(6-skills);return rnd(e.rng.limited(v,v/2));}
void Operator::received(Messages msg){
    if(has(msg,M::CQ)){
        if(state==OS::NeedPrevEnd)set(OS::NeedQso);else if(state==OS::NeedQso)dec();else if(among(state,{OS::NeedNr,OS::NeedCall,OS::NeedCallNr}))state=OS::Failed;else if(state==OS::NeedEnd)state=OS::Done;return;
    }
    if(has(msg,M::Nil)){
        if(state==OS::NeedPrevEnd)set(OS::NeedQso);else if(state==OS::NeedQso)dec();else if(among(state,{OS::NeedNr,OS::NeedCall,OS::NeedCallNr,OS::NeedEnd}))state=OS::Failed;return;
    }
    if(has(msg,M::HisCall)){
        switch(isMyCall()){
            case Match::Yes:if(among(state,{OS::NeedPrevEnd,OS::NeedQso,OS::NeedCallNr}))set(OS::NeedNr);else if(state==OS::NeedCall)set(OS::NeedEnd);break;
            case Match::Almost:if(among(state,{OS::NeedPrevEnd,OS::NeedQso,OS::NeedNr}))set(OS::NeedCallNr);else if(state==OS::NeedEnd)set(OS::NeedCall);break;
            case Match::No:if(state==OS::NeedQso)state=OS::NeedPrevEnd;else if(among(state,{OS::NeedNr,OS::NeedCall,OS::NeedCallNr}))state=OS::Failed;else if(state==OS::NeedEnd)state=OS::Done;break;
        }
    }
    if(has(msg,M::B4)){if(among(state,{OS::NeedPrevEnd,OS::NeedQso}))set(OS::NeedQso);else if(among(state,{OS::NeedNr,OS::NeedEnd}))state=OS::Failed;}
    if(has(msg,M::NR)){
        if(state==OS::NeedQso)state=OS::NeedPrevEnd;
        else if(state==OS::NeedNr){if(e.rng.unit()<0.9||e.cfg.mode==Mode::Hst)set(OS::NeedEnd);}
        else if(state==OS::NeedCallNr){if(e.rng.unit()<0.9||e.cfg.mode==Mode::Hst)set(OS::NeedCall);}
    }
    if(has(msg,M::TU)){if(state==OS::NeedPrevEnd)set(OS::NeedQso);else if(state==OS::NeedEnd)state=OS::Done;}
    if(!e.cfg.lids&&msg==bit(M::Garbage))state=OS::NeedPrevEnd;
    if(state!=OS::NeedPrevEnd)dec();
}
M Operator::reply(){
    switch(state){
        case OS::NeedPrevEnd:case OS::Done:case OS::Failed:return M::None;
        case OS::NeedQso:return M::MyCall;
        case OS::NeedNr:return patience==4||e.rng.unit()<0.3?M::NrQm:M::Agn;
        case OS::NeedCall:if(e.cfg.mode==Mode::Hst||e.rng.unit()>0.5)return M::DeMyCallNr1;return e.rng.unit()>0.25?M::DeMyCallNr2:M::MyCallNr2;
        case OS::NeedCallNr:return e.cfg.mode==Mode::Hst||e.rng.unit()>0.5?M::DeMyCall1:M::DeMyCall2;
        case OS::NeedEnd:if(patience<4)return M::NR;return e.cfg.mode==Mode::Hst||e.rng.unit()<0.9?M::R_NR:M::R_NR2;
    }return M::None;
}
Dx::Dx(Engine& e):Station(e),oper(e){
    hisCall=e.cfg.call;myCall=e.pickCall();oper.call=myCall;oper.skills=1+e.rng.integer(3);oper.set(OS::NeedPrevEnd);
    nrError=e.cfg.lids&&e.rng.unit()<0.1;wpm=e.cfg.mode==Mode::Hst?e.cfg.wpm:rnd(e.cfg.wpm*0.5*(1+e.rng.unit()));
    if(e.cfg.dxWpmMax)wpm=e.cfg.dxWpmMin+e.rng.integer(e.cfg.dxWpmMax-e.cfg.dxWpmMin+1);
    oper.wpm=wpm;
    nr=1+rnd(e.rng.unit()*(e.elapsed()/60)*oper.skills);rst=e.cfg.lids&&e.rng.unit()<0.03?559+10*e.rng.integer(4):599;
    double bw=0.1+e.rng.unit()/2;if(e.cfg.flutter&&e.rng.unit()<0.3)bw=3+e.rng.unit()*30;fading.configure(bw,e.rng);
    amplitude=float(9000+18000*(1+e.rng.uShaped()));pitch=rnd(e.rng.limited(0,300));timeout=Never;state=State::Copying;
}
Samples Dx::block(){auto a=Station::block();if(e.cfg.qsb)fading.apply(a,e.rng);return a;}
void Dx::event(Event ev){
    if(oper.state==OS::Done)return;
    switch(ev){
        case Event::Sent:timeout=e.me->state==State::Sending?Never:oper.replyTimeout();break;
        case Event::Timeout:
            if(state==State::Listening){oper.received(bit(M::None));if(oper.state==OS::Failed){dead=true;return;}state=State::Preparing;}
            if(state==State::Preparing)for(int i=0;i<oper.repeat;i++)send(oper.reply());
            break;
        case Event::MeFinished:
            if(state!=State::Sending){
                if(state==State::Copying)oper.received(e.me->messages);
                else if(state==State::Listening||state==State::Preparing){auto msg=e.me->messages;oper.received(has(msg,M::CQ)||has(msg,M::TU)||has(msg,M::Nil)?msg:bit(M::Garbage));}
                if(oper.state==OS::Failed){dead=true;return;}timeout=oper.delay();state=State::Preparing;
            }break;
        case Event::MeStarted:if(state!=State::Sending)state=State::Copying;timeout=Never;break;
    }
}
Qrn::Qrn(Engine& e):Station(e){envelope.resize(blocks(e.rng.unit())*Block);amplitude=float(1e5*pow(10,2*e.rng.unit()));for(auto& x:envelope)if(e.rng.unit()<0.01)x=float((e.rng.unit()-0.5)*amplitude);state=State::Sending;}
void Qrn::event(Event ev){if(ev==Event::Sent)dead=true;}
Qrm::Qrm(Engine& e):Station(e){patience=1+e.rng.integer(5);myCall=e.pickCall();hisCall=e.cfg.call;amplitude=float(5000+25000*(e.rng.unit()));pitch=rnd(e.rng.limited(0,300));wpm=e.cfg.dxWpmMax?e.cfg.dxWpmMin+e.rng.integer(e.cfg.dxWpmMax-e.cfg.dxWpmMin+1):30+e.rng.integer(20);int n=e.rng.integer(7);send(n==0?M::Qrl:n<3?M::Qrl2:n<6?M::LongCQ:M::Qsy);}
void Qrm::event(Event ev){if(ev==Event::Sent){if(--patience==0)dead=true;else timeout=rnd(e.rng.limited(blocks(4),2));}else if(ev==Event::Timeout)send(M::LongCQ);}
} // namespace mr
