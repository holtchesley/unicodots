# unicodots

Unicodots is a unicode version of [asciidots](https://github.com/aaronduino/asciidots) that's currently in progress. 
As of 2017-08-24, it sorta works, but it has some rough edges. 

Oh, and @ is used for macros instead of addresses. 

It's written in re-frame, since I wanted to see what that framework was like, 

To run, right now you can do
lein cljsbuild once
cd resources/public
python -m SimpleHTTPServer 8000


And then go to http://localhost:8000 .

