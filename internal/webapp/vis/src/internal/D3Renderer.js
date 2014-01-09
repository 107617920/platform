/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

if (!LABKEY.vis.internal) {
    LABKEY.vis.internal = {};
}

LABKEY.vis.internal.Axis = function() {
    // This emulates a lot of the d3.svg.axis() functionality, but adds in the ability for us to have tickHovers,
    // different colored tick & gridlines, etc.
    var scale, orientation, tickFormat = function(v) {return v}, tickHover, ticks, tickSize, tickPadding, gridLineColor,
            transform, axisSel = null, tickSel, textSel, gridLineSel, borderSel, grid;

    var axis = function(selection) {
        var data, textAnchor, textXFn, textYFn, gridLineFn, tickFn, border, gridLineData, hasOverlap, bBoxA, bBoxB, i,
                tickEls, gridLineEls, textEls;

        if (scale.ticks) {
            data = scale.ticks(ticks);
        } else {
            data = scale.domain();
        }
        gridLineData = data;

        if (orientation == 'left') {
            textAnchor = 'end';
            textYFn = function(v) {return -(Math.floor(scale(v)) - .5);};
            textXFn = function() {return -tickSize - 2 - tickPadding};
            tickFn = function(v) {v = -(Math.floor(scale(v)) - .5); return 'M0,' + v + 'L' + -tickSize + ',' + v + 'Z';};
            gridLineFn = function(v) {v = -(Math.floor(scale(v)) - .5); return 'M0,' + v + 'L' + Math.abs(grid.leftEdge - grid.rightEdge) + ',' + v + 'Z';};
            border = 'M0.5,' + (-grid.bottomEdge + 1) + 'L0.5,' + -grid.topEdge + 'Z';
            // Don't overlap gridlines with x axis border.
            if (Math.floor(scale(data[0])) == Math.floor(grid.bottomEdge)) {
                gridLineData = gridLineData.slice(1);
            }
        } else if (orientation == 'right') {
            textAnchor = 'start';
            textYFn = function(v) {return -(Math.floor(scale(v)) - .5);};
            textXFn = function() {return tickSize + 2 + tickPadding};
            tickFn = function(v) {v = -(Math.floor(scale(v)) - .5); return 'M0,' + v + 'L' + tickSize + ',' + v + 'Z';};
            gridLineFn = function(v) {v = -(Math.floor(scale(v)) - .5); return 'M0,' + v + 'L' + -(Math.abs(grid.leftEdge - grid.rightEdge)-1) + ',' + v + 'Z';};
            border = 'M-0.5,' + -(grid.bottomEdge) + 'L-0.5,' + -(grid.topEdge) + 'Z';
            // Don't overlap gridlines with x axis border.
            if (Math.floor(scale(data[0])) == Math.floor(grid.bottomEdge)) {
                gridLineData = gridLineData.slice(1);
            }
        } else {
            // Assume bottom otherwise.
            orientation = 'bottom';
            textAnchor = 'middle';
            textXFn = function(v) {return (Math.floor(scale(v)) +.5);};
            textYFn = function() {return tickSize + 2 + tickPadding};
            tickFn = function(v) {v = (Math.floor(scale(v)) +.5); return 'M' + v + ',0L' + v + ',' + tickSize + 'Z';};
            gridLineFn = function(v) {v = (Math.floor(scale(v)) +.5); return 'M' + v + ',0L' + v + ',' + -Math.abs(grid.topEdge - grid.bottomEdge) + 'Z';};
            border = 'M' + grid.leftEdge + ',0.5' + 'L' + grid.rightEdge + ',0.5Z';
            // Don't overlap gridlines with y-left axis border.
            if (scale(data[0]) == grid.leftEdge) {
                gridLineData = gridLineData.slice(1);
            }
        }

        if (!axisSel) {
            axisSel = selection.append('g').attr('class', 'axis');
        }

        if (transform) {
            axisSel.attr('transform', transform);
        }

        if (!tickSel) {
            tickSel = axisSel.append('g').attr('class', 'tick');
        }

        if (!gridLineSel) {
            gridLineSel = axisSel.append('g').attr('class', 'grid-line');
        }

        if (!textSel) {
            textSel = axisSel.append('g').attr('class', 'tick-text');
        }

        if (tickSize && tickSize > 0) {
            tickEls = tickSel.selectAll('path').data(data);
            tickEls.exit().remove();
            tickEls.enter().append('path');
            tickEls.attr('d', tickFn).attr('stroke', '#000000');
        }

        gridLineEls = gridLineSel.selectAll('path').data(gridLineData);
        gridLineEls.exit().remove();
        gridLineEls.enter().append('path');
        gridLineEls.attr('d', gridLineFn).attr('stroke', gridLineColor);

        if (tickHover) {
            textSel.selectAll('text').data([]).exit().remove(); // If we added a tick hover we'll need to remove the old elements.
            textEls = textSel.selectAll('a').data(data);
            textEls.exit().remove();
            textEls = textEls.enter().append('a').attr('xlink:title', tickHover).append('text');
        } else {
            textSel.selectAll('a').data([]).exit().remove(); // If we removed tick hovers we'll need to remove all of the anchors.
            textEls = textSel.selectAll('text').data(data);
            textEls.exit().remove();
            textEls.enter().append('text');
        }

        textEls.attr('x', textXFn)
                .attr('y', textYFn)
                .attr('text-anchor', textAnchor)
                .text(tickFormat)
                .style('font', '10px arial, verdana, helvetica, sans-serif');

        if (orientation == 'bottom') {
            hasOverlap = false;
            for (i = 0; i < textEls[0].length-1; i++) {
                bBoxA = textEls[0][i].getBBox();
                bBoxB = textEls[0][i+1].getBBox();
                if (bBoxA.x + bBoxA.width >= bBoxB.x) {
                    hasOverlap = true;
                    break;
                }
            }

            if (hasOverlap) {
                textEls.attr('transform', function(v) {return 'rotate(15,' + textXFn(v) + ',' + textYFn(v) + ')';})
                        .attr('text-anchor', 'start');
            } else {
                textEls.attr('transform', '');
            }
        }

        if (!borderSel) {
            borderSel = axisSel.append('g').attr('class', 'border');
        }

        borderSel.selectAll('path').remove();
        borderSel.append('path')
                .attr('stroke', '#000000')
                .attr('d', border);
    };

    axis.ticks = function(t) {ticks = t; return axis;};
    axis.scale = function(s) {scale = s; return axis;};
    axis.orient = function(o) {orientation = o; return axis;};
    axis.tickFormat = function(f) {tickFormat = f; return axis;};
    axis.tickHover = function(h) {tickHover = h; return axis;};
    axis.tickSize = function(s) {tickSize = s; return axis;};
    axis.tickPadding = function(p) {tickPadding = p; return axis;};
    axis.gridLineColor = function(c) {gridLineColor = c; return axis;};
    // This is a reference to the plot's grid object that stores the grid dimensions and positions of edges.
    axis.grid = function(g) {grid = g; return axis;};
    axis.transform = function(t) {transform = t; return axis;};
    
    return axis;
};

LABKEY.vis.internal.D3Renderer = function(plot) {
    var errorMsg, labelElements = null, xAxis = null, leftAxis = null, rightAxis = null;
    var initLabelElements = function() {
        labelElements = {};
        var labels, mainLabel = {}, yLeftLabel = {}, yRightLabel = {}, xLabel = {};

        labels = this.canvas.append('g').attr('class', plot.renderTo + '-labels');

        mainLabel.dom = labels.append('text')
                .attr('x', plot.grid.width / 2)
                .attr('y', 30)
                .attr('text-anchor', 'middle')
                .style('font', '18px verdana, arial, helvetica, sans-serif');

        xLabel.dom = labels.append('text')
                .attr('x', plot.grid.leftEdge + (plot.grid.rightEdge - plot.grid.leftEdge) / 2)
                .attr('y', plot.grid.height - 10)
                .attr('text-anchor', 'middle')
                .style('font', '14px verdana, arial, helvetica, sans-serif');

        yLeftLabel.dom = labels.append('text')
                .attr('text-anchor', 'middle')
                .attr('transform', 'translate(' + (plot.grid.leftEdge - 55) + ',' + (plot.grid.height / 2) + ')rotate(270)')
                .style('font', '14px verdana, arial, helvetica, sans-serif');

        yRightLabel.dom = labels.append('text')
                .attr('text-anchor', 'middle')
                .attr('transform', 'translate(' + (plot.grid.rightEdge + 45) + ',' + (plot.grid.height / 2) + ')rotate(90)')
                .style('font', '14px verdana, arial, helvetica, sans-serif');

        labelElements.main = mainLabel;
        labelElements.y = yLeftLabel;
        labelElements.yLeft = yLeftLabel;
        labelElements.yRight = yRightLabel;
        labelElements.x = xLabel;
    };

    var initClipRect = function() {
        var clipPath = this.canvas.append('defs').append('clipPath').attr('id', plot.renderTo + '-clipPath');
        this.clipRect = clipPath.append('rect')
                .attr('x', plot.grid.leftEdge - 10)
                .attr('y', -(plot.grid.topEdge - plot.grid.bottomEdge + plot.grid.bottomEdge))
                .attr('width', plot.grid.rightEdge - plot.grid.leftEdge + 20)
                .attr('height', (plot.grid.topEdge - plot.grid.bottomEdge) + 12);
    };

    var applyClipRect = function(selection) {
        // TODO: Right now we apply this individually to every geom as it renders. It might be better to apply this to a
        // top level svg group that we put all geom items in.
        selection.attr('clip-path', 'url(#'+ plot.renderTo + '-clipPath)');
    };

    var initCanvas = function() {
        if (!this.canvas) {
            this.canvas = d3.select('#' + plot.renderTo).append('svg');
        }

        if (errorMsg) {
            errorMsg.text('');
        }

        this.canvas.attr('width', plot.grid.width).attr('height', plot.grid.height);
    };

    var renderError = function(msg) {
        // Clear the canvas first.
        this.canvas.selectAll('*').remove();

        if (!errorMsg) {
            errorMsg = this.canvas.append('text')
                    .attr('class', 'vis-error')
                    .attr('x', plot.grid.width / 2)
                    .attr('y', plot.grid.height / 2)
                    .attr('text-anchor', 'middle')
                    .attr('fill', 'red');
        }

        errorMsg.text(msg);
    };

    var renderXAxis = function() {
        if (!xAxis) {
            xAxis = LABKEY.vis.internal.Axis().orient('bottom');
        }

        xAxis.scale(plot.scales.x.scale)
                .grid(plot.grid)
                .tickSize(8)
                .tickPadding(10)
                .ticks(7)
                .gridLineColor(plot.gridLinecolor ? plot.gridLineColor : '#dddddd')
                .transform('translate(0,' + (plot.grid.height - plot.grid.bottomEdge) + ')');

        if (plot.scales.x.tickFormat) {
            xAxis.tickFormat(plot.scales.x.tickFormat);
        }

        if (plot.scales.x.tickHoverText) {
            xAxis.tickHover(plot.scales.x.tickHoverText);
        }

        this.canvas.call(xAxis);
    };

    var renderYLeftAxis = function() {
        if (!leftAxis) {
            leftAxis = LABKEY.vis.internal.Axis().orient('left');
        }

        if (plot.scales.yLeft && plot.scales.yLeft.scale) {
            leftAxis.scale(plot.scales.yLeft.scale)
                    .grid(plot.grid)
                    .tickSize(8)
                    .tickPadding(0)
                    .ticks(10)
                    .gridLineColor(plot.gridLinecolor ? plot.gridLineColor : '#dddddd')
                    .transform('translate(' + plot.grid.leftEdge + ',' + plot.grid.height + ')');

            if (plot.scales.yLeft.tickFormat) {
                leftAxis.tickFormat(plot.scales.yLeft.tickFormat);
            }

            if (plot.scales.yLeft.tickHoverText) {
                leftAxis.tickHover(plot.scales.yLeft.tickHoverText);
            }

            this.canvas.call(leftAxis);
        }
    };

    var renderYRightAxis = function() {
        if (!rightAxis) {
            rightAxis = LABKEY.vis.internal.Axis().orient('right');
        }
        if (plot.scales.yRight && plot.scales.yRight.scale) {
            rightAxis.scale(plot.scales.yRight.scale)
                    .grid(plot.grid)
                    .tickSize(8)
                    .tickPadding(0)
                    .ticks(10)
                    .gridLineColor(plot.gridLinecolor ? plot.gridLineColor : '#dddddd')
                    .transform('translate(' + plot.grid.rightEdge + ',' + plot.grid.height + ')');

            if (plot.scales.yRight.tickFormat) {
                rightAxis.tickFormat(plot.scales.yRight.tickFormat);
            }

            if (plot.scales.yRight.tickHoverText) {
                rightAxis.tickHover(plot.scales.yRight.tickHoverText);
            }

            this.canvas.call(rightAxis);
        }
    };

    var renderGrid = function() {
        // TODO: Determine how to hold onto the axis elements so we dont append a new one each time we render. Also so we
        // can remove them when we clear the grid for rendering or error showing.
        if (plot.clipRect) {
            initClipRect.call(this);
        }
        renderXAxis.call(this);
        renderYLeftAxis.call(this);
        renderYRightAxis.call(this);
    };

    var renderClickArea = function(name) {
        var box, bx, by, bTransform, triangle, tx, ty, tPath,
                pad = 10, r = 4,
                labelEl = labelElements[name],
                bbox = labelEl.dom.node().getBBox(),
                bw = bbox.width + (pad * 2) + (3 * r),
                bh = bbox.height,
                labels = this.canvas.selectAll('.' + plot.renderTo + '-labels');

        if (labelEl.clickArea) {
            labelEl.clickArea.remove();
            labelEl.triangle.remove();
        }

        if (name == 'main') {
            tx = Math.floor(bbox.x + bbox.width + (3 * r)) + .5;
            ty = Math.floor(bbox.y + (bbox.height / 2)) + .5;
            tPath = 'M' + tx + ',' + (ty + r) + ' L ' + (tx - r) + ',' + (ty - r) + ' L ' + (tx + r) + ',' + (ty - r) + 'Z';
            bx = Math.floor(bbox.x - pad) +.5;
            by = Math.floor(bbox.y) +.5;
            bTransform = 'translate(' + bx + ',' + by +')';
        } else if (name == 'x') {
            tx = Math.floor(bbox.x + bbox.width + (3 * r)) + .5;
            ty = Math.floor(bbox.y + (bbox.height / 2)) + .5;
            tPath = 'M' + tx + ',' + (ty - r) + ' L ' + (tx - r) + ',' + (ty + r) + ' L ' + (tx + r) + ',' + (ty + r) + 'Z';
            bx = Math.floor(bbox.x - pad) +.5;
            by = Math.floor(bbox.y) +.5;
            bTransform = 'translate(' + bx + ',' + by +')';
        } else if (name == 'y' || name == 'yLeft') {
            tx = Math.floor(plot.grid.leftEdge - (3*r) - 48) + .5;
            ty = Math.floor((plot.grid.height / 2) - (bbox.width / 2) - (3*r)) + .5;
            tPath = 'M' + (tx + r) + ',' + (ty) + ' L ' + (tx - r) + ',' + (ty - r) + ' L ' + (tx - r) + ',' + (ty + r) + ' Z';
            bx = Math.floor(plot.grid.leftEdge - (bbox.height) - 52) + .5;
            by = Math.floor((plot.grid.height / 2) + (bbox.width / 2) + pad) + .5;
            bTransform = 'translate(' + bx + ',' + by +')rotate(270)';
        } else if (name == 'yRight') {
            tx = Math.floor(plot.grid.rightEdge + (2*r) + 40) + .5;
            ty = Math.floor((plot.grid.height / 2) - (bbox.width / 2) - (3*r)) + .5;
            tPath = 'M' + (tx - r) + ',' + (ty) + ' L ' + (tx + r) + ',' + (ty - r) + ' L ' + (tx + r) + ',' + (ty + r) + ' Z';
            bx = Math.floor(plot.grid.rightEdge + 23 + bbox.height) + .5;
            by = Math.floor((plot.grid.height / 2) + (bbox.width / 2) + pad) + .5;
            bTransform = 'translate(' + bx + ',' + by +')rotate(270)';
        }

        triangle = labels.append('path')
                .attr('d', tPath)
                .attr('stroke', '#000000')
                .attr('fill', '#000000');

        box = labels.append('rect')
                .attr('width', bw)
                .attr('height', bh)
                .attr('transform', bTransform)
                .attr('fill-opacity', 0)
                .attr('stroke', '#000000');

        box.on('mouseover', function() {
            box.attr('stroke', '#777777');
            triangle.attr('stroke', '#777777').attr('fill', '#777777');
        });

        box.on('mouseout', function() {
            box.attr('stroke', '#000000');
            triangle.attr('stroke', '#000000').attr('fill', '#000000');
        });

        labelEl.clickArea = box;
        labelEl.triangle = triangle;
    };

    var addLabelListener = function(label, listenerName, listenerFn) {
        var availableListeners = {
            click: 'click', dblclick:'dblclick', drag: 'drag', hover: 'hover', mousedown: 'mousedown',
            mousemove: 'mousemove', mouseout: 'mouseout', mouseover: 'mouseover', mouseup: 'mouseup',
            touchcancel: 'touchcancel', touchend: 'touchend', touchmove: 'touchmove', touchstart: 'touchstart'
        };

        if (availableListeners[listenerName]) {
            if (labelElements[label]) {
                // Store the listener in the labels object.
                if (!plot.labels[label].listeners) {
                    plot.labels[label].listeners = {};
                }

                plot.labels[label].listeners[listenerName] = listenerFn;
                // Add a .user to the listenerName to keep it scoped by itself. This way we can add our own listeners as well
                // but we'll keep them scoped separately. We have to wrap the listenerFn to we can pass it the event.
                labelElements[label].dom.on(listenerName + '.user', function(){listenerFn(d3.event);});
                if (labelElements[label].clickArea) {
                    labelElements[label].clickArea.on(listenerName + '.user', function(){listenerFn(d3.event);});
                }
                return true;
            } else {
                console.error('The ' + label + ' label is not available.');
                return false;
            }
        } else {
            console.error('The ' + listenerName + ' listener is not available.');
            return false;
        }
    };

    var renderLabel = function(name) {
        if (!labelElements) {
            initLabelElements.call(this);
        }

        if ((name == 'y' || name == 'yLeft') && (!plot.scales.yLeft || (plot.scales.yLeft && !plot.scales.yLeft.scale))) {
            return;
        }

        if (name == 'yRight' && (!plot.scales.yRight || (plot.scales.yRight && !plot.scales.yRight.scale))) {
            return;
        }

        if (this.canvas && plot.labels[name] && plot.labels[name].value) {
            labelElements[name].dom.text(plot.labels[name].value);
            if (plot.labels[name].lookClickable == true) {
                renderClickArea.call(this, name);
            }

            if (plot.labels[name].listeners) {
                for (var listener in plot.labels[name].listeners) {
                    if (plot.labels[name].listeners.hasOwnProperty(listener)) {
                        addLabelListener.call(this, name, listener, plot.labels[name].listeners[listener]);
                    }
                }
            }
        }
    };

    var renderLabels = function() {
        for (var name in plot.labels) {
            if (plot.labels.hasOwnProperty(name)) {
                renderLabel.call(this, name);
            }
        }
    };
    
    var clearGrid = function() {
        this.canvas.selectAll('.layer').remove();
        this.canvas.selectAll('.legend').remove();
    };

    var appendTSpans = function(selection, width) {
        var i, words = selection.datum().text.split(' '), segments = [], partial = '', start = 0;

        for(i = 0; i < words.length; i++) {
            partial = partial + words[i] + ' ';
            selection.text(partial);
            if(selection.node().getBBox().width > width) {
                segments.push(words.slice(start, i).join(' '));
                partial = words[i];
                start = i;
            }
        }

        selection.text('');

        segments.push(words.slice(start, i).join(' '));

        selection.selectAll('tspan').data(segments).enter().append('tspan')
                .text(function(d){return d})
                .attr('dy', function(d, i){return i * 12})
                .attr('x', selection.attr('x'));
    };

    var renderLegendItem = function(selection, plot){
        var i, xPad, glyphX, textX, yAcc, colorAcc, shapeAcc, textNodes, currentItem, cBBox, pBBox;

        selection.attr('font-family', 'verdana, arial, helvetica, sans-serif').attr('font-size', '10px');

        xPad = plot.scales.yRight && plot.scales.yRight.scale ? 40 : 0;
        glyphX = plot.grid.rightEdge + 30 + xPad;
        textX = glyphX + 15;
        yAcc = function(d, i) {return (plot.grid.height - plot.grid.topEdge) + (i * 15);};
        colorAcc = function(d) {return d.color ? d.color : '#000';};
        shapeAcc = function(d) {
            if(d.shape) {
                return d.shape(5);
            }
            return "M" + -5 + "," + -2.5 + "L" + 5 + "," + -2.5 + " " + 5 + "," + 2.5 + " " + -5 + "," + 2.5 + "Z";
        };
        textNodes = selection.append('text').attr('x', textX).attr('y', yAcc);
        textNodes.each(function(){d3.select(this).call(appendTSpans, plot.grid.width - textX);});

        // Now that we've rendered the text, iterate through the nodes and adjust the y values so they no longer overlap.
        // Instead of using selection.each we do this because we need access to the neighboring items.
        for (i = 1; i < selection[0].length; i++) {
            currentItem = selection[0][i];
            cBBox = currentItem.getBBox();
            pBBox = selection[0][i-1].getBBox();

            if (pBBox.y + pBBox.height >= cBBox.y) {
                var newY = parseInt(currentItem.childNodes[0].getAttribute('y')) + Math.abs((pBBox.y + pBBox.height + 3) - cBBox.y);
                currentItem.childNodes[0].setAttribute('y', newY);
            }
        }

        // Append the glyphs.
        selection.append('path')
                .attr('d', shapeAcc)
                .attr('stroke', colorAcc)
                .attr('fill', colorAcc)
                .attr('transform', function(){
                    var sibling = this.parentNode.childNodes[0],
                        y = Math.floor(parseInt(sibling.getAttribute('y'))) - 3.5;
                    return 'translate(' + glyphX + ',' + y + ')';
                });
    };

    var renderLegend = function() {
        var legendData = plot.getLegendData(), legendGroup, legendItems;
        if(legendData.length > 0) {
            if(this.canvas.selectAll('.legend').size() == 0) {
                this.canvas.append('g').attr('class', 'legend');
            }
            legendGroup = this.canvas.select('.legend');
            legendGroup.selectAll('.legend-item').remove(); // remove previous legend items.
            legendItems = legendGroup.selectAll('.legend-item').data(legendData);
            legendItems.exit().remove();
            legendItems.enter().append('g').attr('class', 'legend-item').call(renderLegendItem, plot);
        } else {
            // Remove the legend if it was there previously. Issue 19351.
            this.canvas.select('.legend').remove();
        }
    };

    var getLayer = function(geom){
        var id = plot.renderTo + '-' + geom.index;
        var layer = this.canvas.select('#' + id);

        if(layer.size() == 0) {
            layer = this.canvas.append('g').attr('class', 'layer').attr('id', id);
        }

        return layer;
    };

    var renderPointGeom = function(data, geom) {
        var layer, anchorSel, pointsSel, xAcc, yAcc, xBinWidth = null, yBinWidth = null, defaultShape, translateAcc,
                colorAcc, sizeAcc, shapeAcc, hoverTextAcc;
        layer = getLayer.call(this, geom).attr('transform', 'translate(0,' + plot.grid.height + ')');

        if (geom.xScale.scaleType == 'discrete' && geom.position == 'jitter') {
            xBinWidth = ((plot.grid.rightEdge - plot.grid.leftEdge) / (geom.xScale.scale.domain().length)) / 2;
            xAcc = function(row) {
                var value = geom.getX(row);
                if(value == null) {return null;}
                return value - (xBinWidth / 2) + (Math.random() * xBinWidth);
            };
        } else {
            xAcc = function(row) {return geom.getX(row);};
        }

        if (geom.yScale.scaleType == 'discrete' && geom.position == 'jitter') {
            yBinWidth = ((plot.grid.topEdge - plot.grid.bottomEdge) / (geom.yScale.scale.domain().length)) / 2;
            yAcc = function(row) {
                var value = geom.getY(row);
                if(value == null || isNaN(value)) {return null;}
                return -(value - (yBinWidth / 2) + (Math.random() * yBinWidth));
            }
        } else {
            yAcc = function(row) {
                var value = geom.getY(row);
                if(value == null || isNaN(value)) {return null;}
                return -value;
            };
        }

        translateAcc = function(row) {
            var x = xAcc(row), y = yAcc(row);
            if(x == null || isNaN(x) || y == null || isNaN(y)){
                // Remove the dom node if x/y is null.
                return null;
            }
            return 'translate(' + xAcc(row) + ',' + yAcc(row) + ')';
        };
        colorAcc = geom.colorAes && geom.colorScale ? function(row) {
            return geom.colorScale.scale(geom.colorAes.getValue(row) + geom.layerName);
        } : geom.color;
        sizeAcc = geom.sizeAes && geom.sizeScale ? function(row) {
            return geom.sizeScale.scale(geom.sizeAes.getValue(row));
        } : function() {return geom.size};
        defaultShape = function(row) {
            var circle = function(s) {return "M0," + s + "A" + s + "," + s + " 0 1,1 0," + -s + "A" + s + "," + s + " 0 1,1 0," + s + "Z";};
            return circle(sizeAcc(row));
        };
        shapeAcc = geom.shapeAes && geom.shapeScale ? function(row) {
            return geom.shapeScale.scale(geom.shapeAes.getValue(row) + geom.layerName)(sizeAcc(row));
        } : defaultShape;
        hoverTextAcc = geom.hoverTextAes ? geom.hoverTextAes.getValue : null;

        data = data.filter(function(d) {
            var x = xAcc(d), y = yAcc(d);
            // Note: while we don't actually use the color or shape here, we need to calculate them so so they show up in
            // the legend, even if the points are null.
            if(typeof colorAcc == 'function') { colorAcc(d); }
            if (shapeAcc != defaultShape) { shapeAcc(d);}
            return x !== null && y !== null;
        }, this);

        anchorSel = layer.selectAll('.point').data(data);
        anchorSel.exit().remove();
        anchorSel.enter().append('a').attr('class', 'point').append('path');
        anchorSel.attr('xlink:title', hoverTextAcc);
        pointsSel = anchorSel.selectAll('path');
        pointsSel.attr('d', shapeAcc)
                .attr('fill', colorAcc)
                .attr('stroke', colorAcc)
                .attr('fill-opacity', geom.opacity)
                .attr('stroke-opacity', geom.opacity)
                .attr('transform', translateAcc);

        if (geom.pointClickFnAes) {
            pointsSel.on('click', function(data) {
                geom.pointClickFnAes.value(d3.event, data);
            });
        }

        if (plot.clipRect) {
            applyClipRect.call(this, layer);
        }
    };

    var renderErrorBar = function(layer, plot, geom, data) {
        var colorAcc, sizeAcc, topFn, bottomFn, middleFn, selection, newBars;

        colorAcc = geom.colorAes && geom.colorScale ? function(row) {return geom.colorScale.scale(geom.colorAes.getValue(row) + geom.layerName);} : geom.color;
        sizeAcc = geom.sizeAes && geom.sizeScale ? function(row) {return geom.sizeScale.scale(geom.sizeAes.getValue(row));} : geom.size;
        topFn = function(d) {
            var x, y, value, error;
            x = geom.getX(d);
            value = geom.yAes.getValue(d);
            error = geom.errorAes.getValue(d);
            y = -geom.yScale.scale(value + error);
            return LABKEY.vis.makeLine(x - 6, y, x + 6, y);
        };
        bottomFn = function(d) {
            var x, y, value, error;
            x = geom.getX(d);
            value = geom.yAes.getValue(d);
            error = geom.errorAes.getValue(d);
            y = -geom.yScale.scale(value - error);
            return LABKEY.vis.makeLine(x - 6, y, x + 6, y);
        };
        middleFn = function(d) {
            var x, value, error;
            x = geom.getX(d);
            value = geom.yAes.getValue(d);
            error = geom.errorAes.getValue(d);
            return LABKEY.vis.makeLine(x, -geom.yScale.scale(value + error), x, -geom.yScale.scale(value - error));
        };

        selection = layer.selectAll('.error-bar').data(data);
        selection.exit().remove();

        newBars = selection.enter().append('g').attr('class', 'error-bar');
        newBars.append('path').attr('class','error-bar-top');
        newBars.append('path').attr('class','error-bar-mid');
        newBars.append('path').attr('class','error-bar-bottom');

        selection.selectAll('.error-bar-top').attr('d', topFn).attr('stroke', colorAcc).attr('stroke-width', sizeAcc);
        selection.selectAll('.error-bar-mid').attr('d', bottomFn).attr('stroke', colorAcc).attr('stroke-width', sizeAcc);
        selection.selectAll('.error-bar-bottom').attr('d', middleFn).attr('stroke', colorAcc).attr('stroke-width', sizeAcc);
    };

    var renderErrorBarGeom = function(data, geom) {
        var layer = getLayer.call(this, geom).attr('transform', 'translate(0,' + plot.grid.height + ')');
        layer.call(renderErrorBar, plot, geom, data);

        if (plot.clipRect) {
            applyClipRect.call(this, layer);
        }
    };

    var getBottomWhisker = function(summary) {
        var i = 0, smallestNotOutlier = summary.Q1 - (1.5 * summary.IQR);

        while (summary.sortedValues[i] < smallestNotOutlier) {i++;}

        return summary.sortedValues[i] < summary.Q1 ? summary.sortedValues[i] : null;
    };

    var getTopWhisker = function(summary) {
        var i = summary.sortedValues.length - 1,  largestNotOutlier = summary.Q3 + (1.5 * summary.IQR);

        while (summary.sortedValues[i] > largestNotOutlier) {i--;}

        return summary.sortedValues[i] > summary.Q3 ? summary.sortedValues[i] : null;
    };

    var renderBox = function(layer, plot, geom, data) {
        var heightFn, bBarFn, bWhiskerFn, tBarFn, tWhiskerFn, mLineFn, hoverTextFn, boxSelection, newBoxes;
        var binWidth = (plot.grid.rightEdge - plot.grid.leftEdge) / (geom.xScale.scale.domain().length);
        var width = binWidth / 2;

        heightFn = function(d) {
            return Math.floor(-geom.yScale.scale(d.summary.Q1) - -geom.yScale.scale(d.summary.Q3));
        };
        mLineFn = function(d) {
            var x, y;
            x = geom.xScale.scale(d.name);
            y = Math.floor(-geom.yScale.scale(d.summary.Q2)) + .5;
            return LABKEY.vis.makeLine(x - (width/2), y, x + (width/2), y);
        };
        tBarFn = function(d) {
            var x, y, w;
            w = getTopWhisker(d.summary);

            if(w == null) {return null;}

            y = Math.floor(-geom.yScale.scale(w)) + .5;
            x = geom.xScale.scale(d.name);
            return LABKEY.vis.makeLine(x - width / 4, y, x + width / 4, y);
        };
        tWhiskerFn = function(d) {
            var x, yTop, yBottom, w;
            w = getTopWhisker(d.summary);

            if(w == null) {return null;}

            yTop = Math.floor(-geom.yScale.scale(w)) + .5;
            yBottom = Math.floor(-geom.yScale.scale(d.summary.Q3)) + .5;
            x = geom.xScale.scale(d.name);
            return LABKEY.vis.makeLine(x, yTop, x, yBottom);
        };
        bBarFn = function(d) {
            var x, y, w;
            w = getBottomWhisker(d.summary);

            if(w == null) {return null;}

            y = Math.floor(-geom.yScale.scale(w)) + .5;
            x = geom.xScale.scale(d.name);
            return LABKEY.vis.makeLine(x - width / 4, y, x + width / 4, y);
        };
        bWhiskerFn = function(d) {
            var x, yTop, yBottom, w;
            w = getBottomWhisker(d.summary);

            if(w == null) {return null;}

            yTop = Math.floor(-geom.yScale.scale(d.summary.Q1)) + .5;
            yBottom = Math.floor(-geom.yScale.scale(w)) + .5;
            x = geom.xScale.scale(d.name);
            return LABKEY.vis.makeLine(x, yTop, x, yBottom);
        };
        hoverTextFn = geom.hoverTextAes ? function(d) {return geom.hoverTextAes.value(d.name, d.summary)} : null;

        // Here we group each box with an a tag. Each part of a box plot (rect, whisker, etc.) gets its own class.
        boxSelection = layer.selectAll('.box').data(data);
        boxSelection.exit().remove();

        // Append new rects, whiskers, etc. as necessary.
        newBoxes = boxSelection.enter().append('a').attr('class', 'box');
        newBoxes.append('rect').attr('class', 'box-rect');
        newBoxes.append('path').attr('class', 'box-mline');
        newBoxes.append('path').attr('class', 'box-tbar');
        newBoxes.append('path').attr('class', 'box-bbar');
        newBoxes.append('path').attr('class', 'box-twhisker');
        newBoxes.append('path').attr('class', 'box-bwhisker');

        // Set all attributes.
        boxSelection.attr('xlink:title', hoverTextFn);
        boxSelection.selectAll('.box-rect').attr('x', function(d) {return geom.xScale.scale(d.name) - (binWidth / 4)})
                .attr('y', function(d) {return Math.floor(-geom.yScale.scale(d.summary.Q3)) + .5})
                .attr('width', width).attr('height', heightFn)
                .attr('stroke', geom.color).attr('fill', geom.fill).attr('stroke-width', geom.lineWidth).attr('fill-opacity', geom.opacity);;
        boxSelection.selectAll('.box-mline').attr('d', mLineFn).attr('stroke', geom.color).attr('stroke-width', geom.lineWidth);
        boxSelection.selectAll('.box-tbar').attr('d', tBarFn).attr('stroke', geom.color).attr('stroke-width', geom.lineWidth);
        boxSelection.selectAll('.box-twhisker').attr('d', tWhiskerFn).attr('stroke', geom.color).attr('stroke-width', geom.lineWidth);
        boxSelection.selectAll('.box-bbar').attr('d', bBarFn).attr('stroke', geom.color).attr('stroke-width', geom.lineWidth);
        boxSelection.selectAll('.box-bwhisker').attr('d', bWhiskerFn).attr('stroke', geom.color).attr('stroke-width', geom.lineWidth);
    };

    var renderOutliers = function(layer, plot, geom, data) {
        var xAcc, yAcc, xBinWidth = null, yBinWidth = null, defaultShape, translateAcc, colorAcc, shapeAcc, hoverAcc,
                outlierSel, pathSel;

        data = data.filter(function(d) {
            var x = geom.getX(d), y = geom.getY(d);
            return x !== null && y !== null;
        });

        defaultShape = function() {
            var circle = function(s) {return "M0," + s + "A" + s + "," + s + " 0 1,1 0," + -s + "A" + s + "," + s + " 0 1,1 0," + s + "Z";};
            return circle(geom.outlierSize);
        };

        if (geom.xScale.scaleType == 'discrete' && geom.position == 'jitter') {
            xBinWidth = ((plot.grid.rightEdge - plot.grid.leftEdge) / (geom.xScale.scale.domain().length)) / 2;
            xAcc = function(row) {return geom.getX(row) - (xBinWidth / 2) + (Math.random() * xBinWidth);};
        } else {
            xAcc = function(row) {return geom.getX(row);};
        }

        if (geom.yScale.scaleType == 'discrete' && geom.position == 'jitter') {
            yBinWidth = ((plot.grid.topEdge - plot.grid.bottomEdge) / (geom.yScale.scale.domain().length)) / 2;
            yAcc = function(row) {return -(geom.getY(row) - (yBinWidth / 2) + (Math.random() * yBinWidth));}
        } else {
            yAcc = function(row) {return -geom.getY(row);};
        }

        translateAcc = function(row) {return 'translate(' + xAcc(row) + ',' + yAcc(row) + ')';};
        colorAcc = geom.outlierColorAes && geom.colorScale ? function(row) {
            return geom.colorScale.scale(geom.outlierColorAes.getValue(row) + geom.layerName);
        } : geom.outlierFill;
        shapeAcc = geom.outlierShapeAes && geom.shapeScale ? function(row) {
            return geom.shapeScale.scale(geom.outlierShapeAes.getValue(row))(geom.outlierSize);
        } : defaultShape;
        hoverAcc = geom.outlierHoverTextAes ? geom.outlierHoverTextAes.getValue : null;

        outlierSel = layer.selectAll('.outlier').data(data);
        outlierSel.exit().remove();

        outlierSel.enter().append('a').attr('class', 'outlier').append('path');
        outlierSel.attr('xlink:title', hoverAcc).attr('class', 'outlier');
        pathSel = outlierSel.selectAll('path');
        pathSel.attr('class', 'outlier')
                .attr('d', shapeAcc)
                .attr('transform', translateAcc)
                .attr('fill', colorAcc)
                .attr('stroke', colorAcc)
                .attr('fill-opacity', geom.outlierOpacity)
                .attr('stroke-opacity', geom.outlierOpacity);

        if (geom.pointClickFnAes) {
            pathSel.on('click', function(data) {
               geom.pointClickFnAes.value(d3.event, data);
            });
        }
    };

    var getOutliers = function(rollups, geom) {
        var outliers = [], smallestNotOutlier, largestNotOutlier, y;

        for (var i = 0; i < rollups.length; i++) {
            smallestNotOutlier = rollups[i].summary.Q1 - (1.5 * rollups[i].summary.IQR);
            largestNotOutlier = rollups[i].summary.Q3 + (1.5 * rollups[i].summary.IQR);
            for (var j = 0; j < rollups[i].rawData.length; j++) {
                y = geom.yAes.getValue(rollups[i].rawData[j]);
                if (y != null && (y > largestNotOutlier || y < smallestNotOutlier)) {
                    outliers.push(rollups[i].rawData[j])
                }
            }
        }

        return outliers;
    };

    var renderBoxPlotGeom = function(data, geom) {
        var layer = getLayer.call(this, geom).attr('transform', 'translate(0,' + plot.grid.height + ')');
        var groupName, groupedData, summary, summaries = [];

        if (geom.xScale.scaleType == 'continuous') {
            console.error('Box Plots not supported for continuous data yet.');
            return;
        }

        groupedData = LABKEY.vis.groupData(data, geom.xAes.getValue);
        for (groupName in groupedData) {
            if (groupedData.hasOwnProperty(groupName)) {
                summary = LABKEY.vis.Stat.summary(groupedData[groupName], geom.yAes.getValue);
                if (summary.sortedValues.length > 0) {
                    summaries.push({
                        name: groupName,
                        summary: summary,
                        rawData: groupedData[groupName]
                    });
                }
            }
        }
        if (summaries.length > 0) {
            // Render box
            layer.call(renderBox, plot, geom, summaries);
            if (geom.showOutliers) {
                //Render the outliers.
                layer.call(renderOutliers, plot, geom, getOutliers(summaries, geom));
            }
        }

        if (plot.clipRect) {
            applyClipRect.call(this, layer);
        }
    };

    var renderLines = function(layer, data, geom) {
        var xAcc = function(d) {return geom.getX(d);},
            yAcc = function(d) {var val = geom.getY(d); return val == null ? null : -val;},
            size = geom.sizeAes && geom.sizeScale ? geom.sizeScale.scale(geom.sizeAes.getValue(data)) : function() {return geom.size},
            color = geom.color,
            line = function(d) {return LABKEY.vis.makePath(d.data, xAcc, yAcc);};

        if (geom.groupAes && geom.colorScale) {
            // TODO: This is probably a bug. We totally skip the colorAes. We should alter this so we pass this.colorAes
            // the entire chunk of data then pass that value to the colorScale. This means colorAes for paths would always
            // need to be functions that expect an array of data. Making this change might break already existing charts,
            // so I'm going to leave this as is for now.
            color = function(d) {return geom.colorScale.scale(d.group + geom.layerName);};
        }

        var pathSel = layer.selectAll('path').data(data);
        pathSel.exit().remove();
        pathSel.enter().append('path');
        pathSel.attr('d', line)
                .attr('stroke', color)
                .attr('stroke-width', size)
                .attr('stroke-opacity', geom.opacity)
                .attr('fill', 'none');
    };

    var renderLineGeom = function(data, geom) {
        var layer = getLayer.call(this, geom).attr('transform', 'translate(0,' + plot.grid.height + ')');
        var renderableData = [];

        if (geom.groupAes) {
            var groupedData = LABKEY.vis.groupData(data, geom.groupAes.getValue);
            for (var group in groupedData) {
                if (groupedData.hasOwnProperty(group)) {
                    renderableData.push({
                        group: group,
                        data: groupedData[group]
                    });
                }
            }
        } else { // No groupAes specified, so we connect all points.
            renderableData = [{group: null, data: data}];
        }

        layer.call(renderLines, renderableData, geom);

        if (plot.clipRect) {
            applyClipRect.call(this, layer);
        }
    };

    return {
        initCanvas: initCanvas,
        renderError: renderError,
        renderGrid: renderGrid,
        clearGrid: clearGrid,
        renderLabel: renderLabel,
        renderLabels: renderLabels,
        addLabelListener: addLabelListener,
        renderLegend: renderLegend,
        renderPointGeom: renderPointGeom,
        renderLineGeom: renderLineGeom,
        renderErrorBarGeom: renderErrorBarGeom,
        renderBoxPlotGeom: renderBoxPlotGeom
    };
};
