/*
 * The MIT License
 *
 * Copyright (c) 2004-2017, Sun Microsystems, Inc., Kohsuke Kawaguchi, Brian Westrich, Martin Eigenbrodt, Daniel Beck
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package join;

import hudson.Extension;
import hudson.Launcher;
import hudson.matrix.MatrixAggregatable;
import hudson.matrix.MatrixAggregator;
import hudson.matrix.MatrixBuild;
import hudson.model.BuildListener;

import java.io.IOException;

@Extension(optional = true)
public class MatrixJoinTrigger implements MatrixAggregatable {
    public MatrixAggregator createAggregator(MatrixBuild build, Launcher launcher, BuildListener listener) {
        return new MatrixAggregator(build, launcher, listener) {
            @Override
            public boolean endBuild() throws InterruptedException, IOException {
                JoinTrigger publisher = build.getParent().getPublishersList().get(JoinTrigger.class);
                return publisher == null || publisher.perform(build, launcher, listener);
            }
        };
    }
}
