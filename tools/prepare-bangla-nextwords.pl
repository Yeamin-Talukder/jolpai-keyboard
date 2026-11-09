#!/usr/bin/env perl
# Builds the Bangla next-word lists (bigrams and a small trigram list) the suggestion engine loads.
#
# Two steps: "extract" turns each source into one cleaned sentence per line, "count" counts the
# word pairs and triples and writes the two resource files. Keep the work files outside the repo.
#
#   W="/w/Mobile Application/corpus-work"        # downloads and work files
#   curl -o "$W/wiki/bnwiki-20261101-pages-articles.xml.bz2" https://dumps.wikimedia.org/bnwiki/latest/bnwiki-latest-pages-articles.xml.bz2
#   curl -O https://downloads.wortschatz-leipzig.de/corpora/ben-bd_web_2017_1M.tar.gz      (into $W/leipzig, then
#   curl -O https://downloads.wortschatz-leipzig.de/corpora/ben_newscrawl_2017_1M.tar.gz    unpack the *-sentences.txt)
#   curl -O https://downloads.tatoeba.org/exports/per_language/ben/ben_sentences.tsv.bz2   (into $W/tatoeba, bzip2 -d)
#   perl tools/prepare-bangla-nextwords.pl extract "$W/wiki/bnwiki-20261101-pages-articles.xml.bz2" > "$W/s-wiki.txt"
#   perl tools/prepare-bangla-nextwords.pl extract "$W/leipzig/ben-bd_web_2017_1M-sentences.txt"    > "$W/s-web.txt"
#   perl tools/prepare-bangla-nextwords.pl extract "$W/leipzig/ben_newscrawl_2017_1M-sentences.txt" > "$W/s-news.txt"
#   perl tools/prepare-bangla-nextwords.pl extract "$W/tatoeba/ben_sentences.tsv"                   > "$W/s-tatoeba.txt"
#   LC_ALL=C sort -u -S 1G -T "$W/tmp" "$W"/s-*.txt > "$W/sentences.txt"           # drop repeated sentences
#   LC_ALL=C sort -u -S 1G -T "$W/tmp" "$W"/s-{web,news,tatoeba}.txt > "$W/sentences-nowiki.txt"
#   # bangla-nextwords.tsv: from all sources (CC BY-SA 4.0 because of Wikipedia)
#   perl tools/prepare-bangla-nextwords.pl count "$W/sentences.txt" "$W/out" \
#        "Bangla Wikipedia 2026-11-01 dump (CC BY-SA 4.0), Leipzig Corpora Collection ben-bd_web_2017_1M + ben_newscrawl_2017_1M (CC BY), Tatoeba (CC BY 2.0 FR)"
#   # bangla-trigrams.tsv: without Wikipedia (CC BY 4.0)
#   LICENCE="CC BY 4.0" PARTS=3 MAX_TRI_LINES=20000 BIGRAMS="$W/out/bn/bangla-nextwords.tsv" \
#        perl tools/prepare-bangla-nextwords.pl count "$W/sentences-nowiki.txt" "$W/out-nowiki" \
#        "Leipzig Corpora Collection ben-bd_web_2017_1M + ben_newscrawl_2017_1M (CC BY), Tatoeba (CC BY 2.0 FR)"
#   cp "$W/out/bn/bangla-nextwords.tsv" "$W/out-nowiki/bn/bangla-trigrams.tsv" suggestion-engine/src/main/resources/bn/
#   (each count run writes both files; only the one named above is taken from each run. The first run
#   can be stopped once bangla-nextwords.tsv is written, before its trigram passes.)
#
# Sources (see bn/NOTICE.txt):
#   - Bangla Wikipedia, https://dumps.wikimedia.org/bnwiki/  pages-articles.xml.bz2, CC BY-SA 4.0
#   - Leipzig Corpora Collection, https://wortschatz.uni-leipzig.de/en/download/ben , CC BY
#   - Tatoeba, https://tatoeba.org  Bengali sentence export, CC BY 2.0 FR
# extract accepts a Wikipedia dump (*.xml.bz2 or *.xml), a Leipzig *-sentences.txt ("id<TAB>sentence"),
# a Tatoeba *_sentences.tsv ("id<TAB>ben<TAB>sentence") or any plain UTF-8 text file.
#
# Cleaning (extract):
#   - Wikipedia: main-namespace articles only, no redirects; comments, <ref>s, <math>/<gallery>/...
#     blocks, tables, templates, file/category/interwiki links, URLs and headings removed;
#     [[target|text]] becomes text; list markers and ''bold''/''italic'' quotes stripped.
#   - য় ড় ঢ় written as one character (U+09DF/09DC/09DD), ে+া → ো and ে+ৗ → ৌ, ত্‍ (with ZWJ) → ৎ,
#     then ZWNJ/ZWJ (U+200C/U+200D) removed.
#   - sentences end at । ॥ ? ! . ; … and line breaks.
#   - a word is a run of Bangla letters and signs (U+0981-U+09E3) starting with a letter; Bangla digits,
#     Latin, numbers and punctuation are not words and break the sequence: no pair is counted across
#     them (spaces are the only thing allowed between the two words of a pair).
#   - a sentence whose first token is a word (opening quotes/brackets allowed) starts with <s>, so the
#     list can predict the first word of a sentence. Sentences with fewer than 2 words are dropped.
#   Output line: "<s> w1 w2 | w3 w4" — words separated by spaces, " | " where the sequence breaks.
#
# Counting (count):
#   - previous words: the PREVS (30,000) words most often followed by another word, plus <s>
#   - pairs seen fewer than MIN_PAIR (5) times are dropped; at most MAX_NEXT (6) next words per line
#   - trigram contexts "prev2 prev1": seen (followed by a word) at least MIN_CTX (20) times; triples
#     seen at least MIN_TRI (5) times; at most 3 next words; contexts whose list is the same as the
#     start of prev1's bigram line are left out (they add nothing; BIGRAMS=<file> compares with an
#     existing bigram file instead of this run's); the MAX_TRI_LINES (40,000) most frequent remaining
#     contexts are kept (the shipped file used MAX_TRI_LINES=20000 to stay under 1.5 MB)
#   - weight = round(255 × P(next|prev) / max P(·|prev) of that line), clamped to 1..255.
#     P(next|prev) = count(prev next) / count(prev), so this equals round(255 × count / top count):
#     the first word of every line is 255. Ties are broken by code point order.
#
# Output (UTF-8, LF, sorted by code point, first line is a "#" comment):
#   <out>/bn/bangla-nextwords.tsv   previous<TAB>next1:w1 next2:w2 ...
#   <out>/bn/bangla-trigrams.tsv    prev2 prev1<TAB>next1:w1 next2:w2 next3:w3
use strict;
use warnings;
use utf8;
use open qw(:std :encoding(UTF-8));
use File::Path qw(make_path);
use POSIX qw(strftime);
$| = 1;

my %opt = (
    PREVS => 30000, MIN_PAIR => 5, MAX_NEXT => 6,
    MIN_CTX => 20, MIN_TRI => 5, MAX_TRI_NEXT => 3, MAX_TRI_LINES => 40000,
    PARTS => 8,
);
# Overrides, e.g. PREVS=25000 MIN_PAIR=6 perl tools/prepare-bangla-nextwords.pl count ...
for my $k (keys %opt) { $opt{$k} = $ENV{$k} if defined $ENV{$k} && $ENV{$k} =~ /^\d+$/ }


# ---------------------------------------------------------------- extract

my $LETTER = qr/[\x{0985}-\x{098C}\x{098F}\x{0990}\x{0993}-\x{09A8}\x{09AA}-\x{09B0}\x{09B2}\x{09B6}-\x{09B9}\x{09BD}\x{09CE}\x{09DC}\x{09DD}\x{09DF}-\x{09E1}]/;
my $SIGN   = qr/[\x{0981}-\x{0983}\x{09BC}\x{09BE}-\x{09C4}\x{09C7}\x{09C8}\x{09CB}-\x{09CD}\x{09D7}\x{09E2}\x{09E3}]/;
my $WORD   = qr/^$LETTER(?:$LETTER|$SIGN)*$/;
my $BN     = qr/[\x{0981}-\x{09E3}]/;
my $OPEN   = qr/^["'“‘«(\[{—–-]*$/;   # may precede the first word of a sentence

my ($n_in, $n_out) = (0, 0);

sub extract {
    my ($in) = @_;
    die "extract: missing input\n" unless defined $in;
    my $fh;
    if ($in =~ /\.bz2$/) { open $fh, '-|:encoding(UTF-8)', 'bzip2', '-dc', $in or die "$in: $!" }
    else { open $fh, '<:encoding(UTF-8)', $in or die "$in: $!" }
    if ($in =~ /\.xml(\.bz2)?$/) { extract_wiki($fh) }
    else {
        my $numbered = $in =~ /[-_]sentences\.(?:txt|tsv)$/;   # Leipzig "id<TAB>text", Tatoeba "id<TAB>ben<TAB>text"
        while (my $line = <$fh>) {
            $line =~ s/\r?\n$//;
            $line =~ s/^\d+\t(?:[a-z]{3}\t)?// if $numbered;
            $n_in++;
            emit_text($line);
        }
    }
    close $fh;
    print STDERR "$in: $n_in units in, $n_out sentences out\n";
}

sub extract_wiki {
    my ($fh) = @_;
    my ($ns, $redirect, $intext, $text) = (-1, 0, 0, '');
    while (my $line = <$fh>) {
        if ($intext) {
            if ($line =~ s{</text>.*}{}s) { $text .= $line; $intext = 0; page($text) if $ns == 0 && !$redirect }
            else { $text .= $line }
            next;
        }
        if ($line =~ /<page>/) { ($ns, $redirect, $text) = (-1, 0, '') }
        elsif ($line =~ m{<ns>(\d+)</ns>}) { $ns = $1 }
        elsif ($line =~ /<redirect/) { $redirect = 1 }
        elsif ($line =~ s{^.*?<text[^>]*?(/?)>}{}s) {
            next if $1;   # <text ... />
            if ($line =~ s{</text>.*}{}s) { $text = $line; page($text) if $ns == 0 && !$redirect }
            else { $text = $line; $intext = 1 }
        }
    }
}

sub page {
    my ($t) = @_;
    $n_in++;
    print STDERR "  $n_in articles\n" if $n_in % 20000 == 0;
    for ($t) {
        s/&lt;/</g; s/&gt;/>/g; s/&quot;/"/g; s/&#0?39;/'/g; s/&amp;/&/g;
        s/<!--.*?-->//gs;
        s{<ref\b[^>]*/>}{ }gis;
        s{<ref\b[^>]*>.*?</ref\s*>}{ }gis;
        s{<(math|gallery|syntaxhighlight|source|pre|code|timeline|score|chem|ce|hiero|graph|mapframe|maplink|templatedata|templatestyles|imagemap|references|nowiki)\b[^>]*?(?:/>|>.*?</\1\s*>)}{ | }gis;
        s/<[^<>]*>/ | /g;
        s/&nbsp;/ /g;
        s/&[#\w]+;/ | /g;
        my $i = 0;
        1 while $i++ < 30 && s/\{\{[^{}]*\}\}/ | /g;   # templates, innermost first
        s/\{\{|\}\}/ | /g;
        $i = 0;
        1 while $i++ < 10 && s/\{\|(?:(?!\{\|).)*?\|\}/\n/gs;   # tables
        $i = 0;
        1 while $i++ < 10 && s/\[\[([^\[\]]*)\]\]/link_text($1)/ge;
        s/\[\[|\]\]/ | /g;
        s{\[(?:https?:)?//[^\s\]]*\s*([^\]]*)\]}{ | $1 | }g;
        s{(?:https?|ftp)://\S+}{ | }g;
        s/__[A-Z]+__//g;
        s/'{2,}//g;
    }
    for my $line (split /\n/, $t) {
        next if $line =~ /^\s*=.*=\s*$/;   # heading
        next if $line =~ /^\s*[|!]/;        # table leftovers
        $line =~ s/^[*#:;]+//;
        emit_text($line);
    }
}

sub link_text {
    my ($l) = @_;
    my ($target, @rest) = split /\|/, $l, -1;
    return " | " if !defined $target || $target =~ /:/;         # File:, Category:, বিষয়শ্রেণী:, interwiki ...
    my $text = @rest ? $rest[-1] : $target;
    return $text;
}

sub normalise {
    for ($_[0]) {
        tr/\x{2028}\x{2029}/\n\n/;
        s/\x{09AF}\x{09BC}/\x{09DF}/g;   # য + ় → য়
        s/\x{09A1}\x{09BC}/\x{09DC}/g;   # ড + ় → ড়
        s/\x{09A2}\x{09BC}/\x{09DD}/g;   # ঢ + ় → ঢ়
        s/\x{09C7}\x{09BE}/\x{09CB}/g;   # ে + া → ো
        s/\x{09C7}\x{09D7}/\x{09CC}/g;   # ে + ৗ → ৌ
        s/\x{09A4}\x{09CD}\x{200D}/\x{09CE}/g;   # ত্‍ → ৎ
        s/[\x{200C}\x{200D}\x{FEFF}\x{00AD}]//g;
    }
}

sub emit_text {
    my ($text) = @_;
    return unless $text =~ $BN;
    normalise($text);
    for my $s (split /[\n\x{0964}\x{0965}?!.;…]+/, $text) {
        my @tok;            # words and '|' breaks
        my $start = 1;      # still at the start of the sentence
        my $brk = sub { push @tok, '|' if @tok && $tok[-1] ne '|' };
        for my $chunk (split ' ', $s) {
            my ($lead, $w, $trail) = $chunk =~ /^([^\x{0981}-\x{09E3}]*)([\x{0981}-\x{09E3}]+)([^\x{0981}-\x{09E3}]*)$/;
            if (defined $w && $w =~ $WORD) {
                if ($lead ne '' && !($start && $lead =~ $OPEN)) { $brk->(); $start = 0 }
                push @tok, '<s>' if $start;
                push @tok, $w;
                $brk->() if $trail ne '';
                $start = 0;
            } else {
                $brk->();
                $start = 0 unless $start && $chunk =~ $OPEN;
            }
        }
        pop @tok if @tok && $tok[-1] eq '|';
        my $words = grep { $_ ne '|' && $_ ne '<s>' } @tok;
        next if $words < 2;
        my $line = join ' ', @tok;
        print "$line\n";
        $n_out++;
    }
}

# ---------------------------------------------------------------- count
# Works on the raw UTF-8 bytes of the sentence file (much faster than decoding every pass);
# sorting UTF-8 bytes gives the same order as sorting code points.

sub fragments {   # calls $cb->(\@words) for every unbroken word run of every sentence
    my ($file, $cb) = @_;
    open my $fh, '<:raw', $file or die "$file: $!";
    while (my $line = <$fh>) {
        chomp $line;
        for my $frag (split / \| /, $line) {
            my @w = split / /, $frag;
            $cb->(\@w) if @w >= 2;
        }
    }
    close $fh;
}

sub weights {   # [[word, count] ...] sorted best first → "w:255 w2:180 ..."
    my ($list) = @_;
    my $top = $list->[0][1];
    return join ' ', map {
        my $wt = int(255 * $_->[1] / $top + 0.5);
        $wt = 1 if $wt < 1;
        $wt = 255 if $wt > 255;
        "$_->[0]:$wt";
    } @$list;
}

sub count {
    my ($file, $out, $source) = @_;
    die "count: <sentences.txt> <out dir> [source]\n" unless defined $out;
    $source //= 'see bn/NOTICE.txt';
    my $licence = $ENV{LICENCE} // q(CC BY-SA 4.0);
    my $date = strftime('%Y-%m-%d', localtime);
    make_path("$out/bn");
    my $K = $opt{PARTS};

    # Pass 1: how often each word occurs and how often it is followed by another word.
    my (%total, %prevc, $sentences, $tokens);
    open my $fh, '<:raw', $file or die "$file: $!";
    while (my $line = <$fh>) {
        chomp $line;
        $sentences++;
        for my $frag (split / \| /, $line) {
            my @w = split / /, $frag;
            for my $i (0 .. $#w) {
                $total{$w[$i]}++;
                $prevc{$w[$i]}++ if $i < $#w;
            }
        }
    }
    close $fh;
    $tokens = 0;
    $tokens += $total{$_} for keys %total;
    $tokens -= $total{'<s>'} // 0;
    printf "corpus: %d sentences, %d word tokens, %d distinct words\n", $sentences, $tokens, scalar(keys %total) - 1;

    my @prev = grep { $_ ne '<s>' } sort { $prevc{$b} <=> $prevc{$a} || $a cmp $b } keys %prevc;
    splice @prev, $opt{PREVS} if @prev > $opt{PREVS};
    unshift @prev, '<s>';
    my %isprev = map { $_ => 1 } @prev;
    my %vocab;   # possible next words
    for (keys %total) { $vocab{$_} = 1 if $total{$_} >= $opt{MIN_PAIR} && $_ ne '<s>' }
    my %part;    # partition of words that can start a pair or a context
    my $id = 0;
    for my $w (sort keys %prevc) {
        $part{$w} = $id++ % $K if $isprev{$w} || $prevc{$w} >= $opt{MIN_CTX};
    }
    undef %total;

    # Pass 2 (K times): pairs whose first word is in partition k, and trigram contexts.
    my (%line, %ctx, %bigram_top);
    my $pairs = 0;
    for my $k (0 .. $K - 1) {
        my (%big, %c);
        fragments($file, sub {
            my $w = shift;
            for my $i (0 .. $#$w - 1) {
                my $p = $part{$w->[$i]};
                next unless defined $p && $p == $k;
                my ($x, $y) = ($w->[$i], $w->[$i + 1]);
                $big{"$x\t$y"}++ if $isprev{$x} && $vocab{$y};
                $c{"$x $y"}++ if $i + 2 <= $#$w && defined $part{$y};
            }
        });
        my %by;
        while (my ($key, $n) = each %big) {
            next if $n < $opt{MIN_PAIR};
            my ($x, $y) = split /\t/, $key;
            push @{ $by{$x} }, [$y, $n];
        }
        undef %big;
        for my $x (keys %by) {
            my @l = sort { $b->[1] <=> $a->[1] || $a->[0] cmp $b->[0] } @{ $by{$x} };
            splice @l, $opt{MAX_NEXT} if @l > $opt{MAX_NEXT};
            $pairs += @l;
            $line{$x} = weights(\@l);
            $bigram_top{$x} = join ' ', map { $_->[0] } @l;
        }
        while (my ($key, $n) = each %c) { $ctx{$key} = $n if $n >= $opt{MIN_CTX} }
        printf "  pass 2.%d: %d previous words so far, %d contexts\n", $k, scalar(keys %line), scalar(keys %ctx);
    }
    undef %part;

    my $bf = "$out/bn/bangla-nextwords.tsv";
    open my $o, '>:raw', $bf or die "$bf: $!";
    print $o "# Jolpai Bangla next words (previous<TAB>next:weight ..., weight 1-255). Source: $source. "
      . "Licence: $licence (see NOTICE.txt). Built $date by tools/prepare-bangla-nextwords.pl\n";
    print $o "$_\t$line{$_}\n" for sort keys %line;
    close $o;
    printf "%s: %d lines, %d pairs, %d bytes\n", $bf, scalar(keys %line), $pairs, -s $bf;

    # BIGRAMS=<file>: compare trigram lists with that (shipped) bigram file instead of this run's.
    if (defined $ENV{BIGRAMS}) {
        %bigram_top = ();
        open my $bh, '<:raw', $ENV{BIGRAMS} or die "$ENV{BIGRAMS}: $!";
        while (my $l = <$bh>) {
            next if $l =~ /^#/;
            chomp $l;
            my ($p, $list) = split /\t/, $l;
            $bigram_top{$p} = join ' ', map { (split /:/)[0] } split / /, $list;
        }
        close $bh;
    }

    # Pass 3 (K times): triples for the contexts in partition k.
    my %cpart;
    $id = 0;
    $cpart{$_} = $id++ % $K for sort keys %ctx;
    my %tri_line;
    my ($same, $tri_pairs) = (0, 0);
    for my $k (0 .. $K - 1) {
        my %t;
        fragments($file, sub {
            my $w = shift;
            for my $i (0 .. $#$w - 2) {
                next unless $vocab{$w->[$i + 2]};
                my $c = "$w->[$i] $w->[$i + 1]";
                my $p = $cpart{$c};
                next unless defined $p && $p == $k;
                $t{"$c\t$w->[$i + 2]"}++;
            }
        });
        my %by;
        while (my ($key, $n) = each %t) {
            next if $n < $opt{MIN_TRI};
            my ($c, $x) = split /\t/, $key;
            push @{ $by{$c} }, [$x, $n];
        }
        undef %t;
        for my $c (keys %by) {
            my @l = sort { $b->[1] <=> $a->[1] || $a->[0] cmp $b->[0] } @{ $by{$c} };
            splice @l, $opt{MAX_TRI_NEXT} if @l > $opt{MAX_TRI_NEXT};
            my ($p2, $p1) = split / /, $c;
            my $list = join ' ', map { $_->[0] } @l;
            my $bt = $bigram_top{$p1} // '';
            if ($bt eq $list || index($bt, "$list ") == 0) { $same++; next }
            $tri_line{$c} = weights(\@l);
        }
        printf "  pass 3.%d: %d trigram contexts so far\n", $k, scalar(keys %tri_line);
    }
    my @keep = sort { $ctx{$b} <=> $ctx{$a} || $a cmp $b } keys %tri_line;
    splice @keep, $opt{MAX_TRI_LINES} if @keep > $opt{MAX_TRI_LINES};
    my $tf = "$out/bn/bangla-trigrams.tsv";
    open $o, '>:raw', $tf or die "$tf: $!";
    print $o "# Jolpai Bangla trigrams (prev2 prev1<TAB>next:weight ..., weight 1-255). Source: $source. "
      . "Licence: $licence (see NOTICE.txt). Built $date by tools/prepare-bangla-nextwords.pl\n";
    for my $c (sort @keep) {
        print $o "$c\t$tri_line{$c}\n";
        $tri_pairs += () = $tri_line{$c} =~ / /g;
        $tri_pairs++;
    }
    close $o;
    printf "%s: %d lines, %d triples, %d bytes (%d contexts matched the bigram list and were left out)\n",
      $tf, scalar(@keep), $tri_pairs, -s $tf, $same;
}

# ---------------------------------------------------------------- main

my $cmd = shift @ARGV // '';
if ($cmd eq 'extract') { extract(@ARGV) }
elsif ($cmd eq 'count') { count(@ARGV) }
else {
    die "usage: $0 extract <wiki dump .xml.bz2 | leipzig -sentences.txt | text file> > sentences.txt\n"
      . "       $0 count <sentences.txt> <out resources dir> [source description]\n";
}
