function objectifyForm(formArray) {//serialize data function

  var returnArray = {};
  for (var i = 0; i < formArray.length; i++){
    returnArray[formArray[i]['name']] = formArray[i]['value'];
  }
  return returnArray;
}


$(document).ready(function() {
    $('#main-menu .options button').each(function() {
        var $btn = $(this);
        var resource = $btn.attr('data-resource');
        var data = {};
        var redirected = $('.back-button').attr('data-clicked');
        if(redirected=='true') {
            data['redirected'] = redirected;
        }
        $btn.click(function(e) {
            e.stopPropagation();
            e.preventDefault();
            $.ajax({
                url: '/resources/'+resource,
                dataType: 'json',
                type: 'GET',
                data: data,
                success: function(data) {
                    var $results = $('#results');
                    $results.empty();
                    $results.html(createResourceList(resource, $btn.text(),data));
                    onShowResourceFunction($(document.body));
                    createResourceDynatable(resource);
                }
            });
        });
    });

    $('.change-password-link').click(function(e) {
        e.stopPropagation();
        e.preventDefault();
        $(this).next().slideToggle();
    });

    $('.change-password-form').submit(function(e) {
        e.preventDefault();
        var $form = $(this);
        $.ajax({
            url: '/update_password',
            dataType: 'json',
            data: $form.serialize(),
            type: 'POST',
            success: function(data) {
                if(data.hasOwnProperty('success')) {
                    $form.hide();
                    $form.find('input.form-control').val(null);
                } else {
                    alert(data.result);
                }
            },
            error: function() {
                alert("Error changing password.");
            }
        });
    });
});

var createResourceDynatable = function(resource) {
    $.ajax({
        url: '/init/datatable/'+resource,
        dataType: 'json',
        type: 'POST',
        success: function(data) {
            var $prevTable = $('table.dynatable');
            if($prevTable.length>0) {
                $prevTable.closest('.row').remove();
            }
            var $results = $('#results');
            //$results.empty();
            $results.append(data.result);
            var $table = $('table.dynatable');
            $table.bind('dynatable:afterUpdate', function() {
                onShowResourceFunction($table);

            }).dynatable({
                dataset: {
                    ajax: true,
                    ajaxUrl: 'dataTable.json',
                    ajaxOnLoad: true,
                    records: []
                },
                features: {
                    pushState: false,
                    paginate: true
                }
            });
        }
    });
};


var showDiagramFunction = function(id,resourceId,$target) {
    var inDiagram = $target && $target.length > 0;
    $.ajax({
        url: '/diagram/'+resourceId+'/'+id,
        dataType: 'json',
        data: {
            in_diagram: inDiagram
        },
        type: 'GET',
        success: function(data) {
            var $result = null;
            if(inDiagram) {
                $result = $target;
                $result.html($(data.result).children());
            } else {
                $result = $('#results');
                $result.html(data.result);
            }
            onShowResourceFunction($result);
        },
        error: function() {
            alert("An error occurred.");
        }
    });
};


var showGraphsFunction = function(id,resourceId) {
    $.ajax({
        url: '/graph/'+resourceId+'/'+id,
        dataType: 'json',
        type: 'GET',
        success: function(data) {
            $('#results').html(data.result);
            onShowResourceFunction($('#results'));
        },
        error: function() {
            alert("An error occurred.");
        }
    });
};


var showComparisonFunction = function(id,resourceId) {
    $.ajax({
        url: '/comparison/'+resourceId+'/'+id,
        dataType: 'json',
        type: 'GET',
        success: function(data) {
            $('#results').html(data.result);
            onShowResourceFunction($('#results'));
        },
        error: function() {
            alert("An error occurred.");
        }
    });
};


var showReportFunction = function(id,resourceId) {
    $.ajax({
        url: '/report/'+resourceId+'/'+id,
        dataType: 'json',
        type: 'GET',
        success: function(data) {
            $('#results').html(data.result);
            onShowResourceFunction($('#results'));
        },
        error: function() {
            alert("An error occurred.");
        }
    });
};

var formatRevenueString = function(revenue) {
    if(revenue===null) { return ""; }
    return '$' + parseFloat(Math.round(revenue * 100) / 100).toFixed(2);
};


var updateAssociationTotals = function() {
    $('.association-revenue-totals').each(function() {
        var $this = $(this);
        var sum = 0.0;
        $this.next().children().each(function() {
            $(this).children().filter('.resource-data-field').each(function() {
                var $field = $(this);
                if($field.attr('data-val')) {
                    sum += parseFloat($field.attr('data-val'));
                }
            });
        });
        $this.text('(Revenue: '+formatRevenueString(sum)+")");
    });
};

var isListening = false;
var updateResourceFormHelper = function($this) {
    $('.resource-data-field.editable').attr('data-opened', 'false');
    if(!isListening) {
        return;
    }
    isListening=false;
    var resourceId = $this.attr('data-resource');
    var id = $this.attr("data-id");
    var $form = $this;
    var data = objectifyForm($form.serializeArray());
    if(!data) {
        data = {};
    }
    var $checkboxes = $('input.form-checkbox');
    if($checkboxes.length>0) {
        $checkboxes.each(function() {
            data[$(this).attr('name')]=$(this).prop('checked');
        });
    }
    $.ajax({
        url: '/resources/'+resourceId+'/'+id,
        dataType: 'json',
        type: 'POST',
        data: data,
        success: function(showData) {
            if(showData.hasOwnProperty('error')) {
                alert(showData.error);
                showResourceFunction(resourceId, id);

            } else {
                $(document.body).off('dblclick');
                // refresh // keep tab
                showResourceFunction(resourceId, id);
            }
        },
        error: function() {
            alert("An error occurred.");
        }
    });
}


var onShowResourceFunction = function($topElem) {
    updateAssociationTotals();

    $topElem.find(".revenue_domain").on('change', function(e) {
        var val = $(this).val();
        var $regional = $('.revenue-regional');
        var $national = $('.revenue-national');
        if(val==='global') {
            $national.prop('disabled', true).parent().hide();
            $regional.prop('disabled', true).parent().hide();
        } else if(val === 'regional') {
            $national.prop('disabled', true).parent().hide();
            $regional.prop('disabled', false).parent().css('display', 'block');
        } else if(val === 'national') {
            $national.prop('disabled', false).parent().css('display', 'block');
            $regional.prop('disabled', true).parent().hide();
        } else {
            alert('Unknown revenue domain.');
        }
    });

    $topElem.find('#report-specification-form').submit(function(e) {
        e.preventDefault();
        var $form = $(this);
        var id = $form.attr("data-id");
        var resourceId = $form.attr('data-resource');
        $.ajax({
            url: '/generate-report/'+resourceId+'/'+id,
            dataType: 'json',
            data: $form.serialize(),
            type: 'POST',
            success: function(data) {
                if(data.hasOwnProperty('error')) {
                    alert(data.error);
                    if(data.hasOwnProperty('helper')) {
                        $('#inner-results').html(data.helper);
                        onShowResourceFunction($('#inner-results'));

                    } else {
                        $('#inner-results').html('');
                    }
                } else {
                    $('#inner-results').html(data.result);
                    onShowResourceFunction($('#inner-results'));
                }
            },
            error: function() {
                alert("An error occurred.");
            }
        });
    });

    $topElem.find('#comparison-specification-form').submit(function(e) {
        e.preventDefault();

        var $form = $(this);
        var id = $form.attr("data-id");
        var resourceId = $form.attr('data-resource');
        var otherId = $('#compare-model-select').val();
        if(!otherId) {
            alert("Please specify a "+ resourceId + " to compare.");
            return;
        }
        $.ajax({
            url: '/generate-comparison/'+resourceId+'/'+id+'/'+otherId,
            dataType: 'json',
            data: $form.serialize(),
            type: 'POST',
            success: function(data) {
                if(data.hasOwnProperty('error')) {
                    alert(data.error);
                    if(data.hasOwnProperty('helper')) {
                        $('#inner-results').html(data.helper);
                        onShowResourceFunction($('#inner-results'));

                    } else {
                        $('#inner-results').html('');
                    }
                } else {
                    $('#inner-results').html(data.result);
                    onShowResourceFunction($('#inner-results'));
                }
            },
            error: function() {
                alert("An error occurred.");
            }
        });
    });

    $topElem.find('#graph-specification-form').submit(function(e) {
        e.preventDefault();
        var $form = $(this);
        var id = $form.attr("data-id");
        var resourceId = $form.attr('data-resource');
        $.ajax({
            url: '/generate-graph/'+resourceId+'/'+id,
            dataType: 'json',
            data: $form.serialize(),
            type: 'POST',
            success: function(data) {
                if(data.hasOwnProperty('error')) {
                    alert(data.error);
                    if(data.hasOwnProperty('helper')) {
                        $('#inner-results').html(data.helper);
                        onShowResourceFunction($('#inner-results'));

                    } else {
                        $('#inner-results').html('');
                    }
                } else {
                    var $innerResults = $('#inner-results');
                    var i = 0;
                    while(data.hasOwnProperty('chart_'+i.toString())) {
                        var chartId = 'chart_'+i.toString();
                        $innerResults.append('<div align="center" id="chart_'+i.toString()+'"></div>');
                        Highcharts.chart(chartId, JSON.parse(data[chartId]));
                        i = i+1;
                    }
                    onShowResourceFunction($('#inner-results'));
                }
            },
            error: function() {
                alert("An error occurred.");
            }
        });
    });


    //$('.resource-data-field').not('.editable').css('cursor', 'not-allowed');
    $topElem.find('.resource-data-field.editable').css('cursor', 'cell');

    // delete node
    $topElem.find('.delete-button').click(function(e) {
        e.preventDefault();
        e.stopPropagation();
        var $this = $(this);
        var resourceId = $this.attr('data-resource');
        var resourceName = $this.attr('data-resource-name');
        if(confirm("Are you sure you want to delete this "+resourceName+"?")) {
            var id = $this.attr('data-id');
            var url = '/resources/'+resourceId+'/'+id;
            $.ajax({
                url: url,
                dataType: 'json',
                type: 'DELETE',
                success: function(showData) {
                    if(showData.hasOwnProperty('error')) {
                        alert(showData.error);
                    } else {
                        $('#results').html('');
                    }
                },
                error: function() {
                    alert("An error occurred.");
                }
            });
        }
    });

    $topElem.find('.diagram-button').click(function(e) {
        e.stopPropagation();
        e.preventDefault();
        var $this = $(this);
        var id = $this.attr('data-id');
        var resourceId = $this.attr('data-resource');
        var $target = null;
        if ($this.hasClass('nested')) {
            $target = $this.closest('ul');
        }
        showDiagramFunction(id,resourceId,$target);
    });

    $topElem.find('.report-button').click(function(e) {
        e.stopPropagation();
        e.preventDefault();
        var $this = $(this);
        var id = $this.attr('data-id');
        var resourceId = $this.attr('data-resource');
        showReportFunction(id,resourceId);
    });

    $topElem.find('.graph-button').click(function(e) {
        e.stopPropagation();
        e.preventDefault();
        var $this = $(this);
        var id = $this.attr('data-id');
        var resourceId = $this.attr('data-resource');
        showGraphsFunction(id,resourceId);
    });

    $topElem.find('.comparison-button').click(function(e) {
        e.stopPropagation();
        e.preventDefault();
        var $this = $(this);
        var id = $this.attr('data-id');
        var resourceId = $this.attr('data-resource');
        showComparisonFunction(id,resourceId);
    });

    $topElem.find('.resource-data-field.editable').dblclick(function(e) {
        var $this = $(this);
        if($this.attr('data-opened')!='true') {
            e.stopPropagation();
        } else {
            return;
        }
        $this.attr('data-opened', 'true');
        var fieldType = $this.attr('data-field-type');
        var resourceId = $this.attr('data-resource');
        var val = $this.attr('data-val');
        var origText = $this.attr('data-val-text');
        var attr = $this.attr('data-attr');
        var attrName = $this.attr('data-attrname');
        var id = $this.attr('data-id');
        var updateOtherFieldsByClassName = $this.attr('data-update-class');
        var input = null;
        var inputTag = null;
        if (fieldType==='textarea') {
            input = "<textarea class='form-control'/>";
            inputTag = "textarea";
        } else if (fieldType==='text') {
            input = "<input type='text' class='form-control' />";
            inputTag = "input";
        } else if (fieldType==='number') {
            input = "<input type='number' class='form-control' />";
            inputTag = "input";
        } else if (fieldType==='boolean') {
            input = "<input class='form-checkbox' type='checkbox' value='t' />";
            if(val && val!='false' && val != '') {
                input = "<input class='form-checkbox' type='checkbox' checked='true' value='true' />";
            }
            inputTag = "input";
        } else if (fieldType==='estimate_type') {
            input = "<select class='multiselect'><option selected='true'></option><option value='0'>Low</option><option value='1'>Medium</option><option value='2'>High</option></select>";
            inputTag = "select";
        }
        $this.html('<label>'+attrName+":"+input+"</label><span style='cursor: pointer;'>X</span><br />");
        $this.find('span').click(function(e) {
            e.preventDefault();
            $this.html(attrName+": "+origText);
            $this.attr('data-opened', 'false')
        });
        var $input = $this.find(inputTag);
        if(fieldType==='estimate_type') {
            $input.select2({
                    minimumResultsForSearch: 5,
                    closeOnSelect: true
                });
        }
        $input.attr('name', attr);
        $input.val(val).filter('select').trigger('change');
        $this.find('input,select,textarea,label').dblclick(function(e) {
            e.stopPropagation();
        });
        if(!isListening) {
            $(document.body).dblclick(function() {
                $(this).off('dblclick');
                updateResourceFormHelper($this.closest('form'));
            });
        }
        isListening = true;
    });

    $topElem.find('form.update-model-form').off('submit');
    $topElem.find('form.update-model-form').submit(function(e) {
        e.preventDefault();
        updateResourceFormHelper($(this));
    });

    $topElem.find('.back-button').click(function(e) {
        $(this).attr('data-clicked', 'true');
        e.stopPropagation();
        e.preventDefault();
        $.ajax({
            url: '/back',
            dataType: 'json',
            type: 'GET',
            success: function(showData) {
                if(showData.hasOwnProperty('error')) {
                    alert(showData.error);
                } else if(showData.hasOwnProperty('template')) {
                    // get show
                    var $results = $('#results');
                    $results.html(showData.template);
                    onShowResourceFunction($results);
                    $('#results .nav.nav-tabs .nav-link').filter(':first').trigger('click');
                } else if (showData.hasOwnProperty('resource_list_show')) {
                    // show resource list
                    $(showData.resource_list_show).trigger('click'); // easier than reconstructing from scratch

                } else if(showData.hasOwnProperty('result')) {
                    if(showData.result && showData.result.length>0) {
                        var $results = $('#results');
                        $results.html(showData.result);
                        onShowResourceFunction($results);
                    }
                }
            }
        });
    });

    $topElem.find('.resource-new-link').click(function(e) {
        e.stopPropagation();
        e.preventDefault();
        $(this).next().slideToggle();
    });
    $topElem.find('form.association').submit(function(e) {
        e.preventDefault();
        var $form = $(this);
        var resourceId = $form.attr("data-resource");
        var associationId = $form.attr("data-association");
        var id = $form.attr('data-id');
        var listRef = $form.attr('data-list-ref');
        var report = $form.attr('data-report');
        var formData = $form.serialize();
        var prepend = $form.attr('data-prepend');
        var refresh = $form.attr('data-refresh');
        var originalId = $form.attr('data-original-id');
        var originalResourceId = $form.attr('data-original-resource');
        $.ajax({
            url: '/new/'+associationId,
            dataType: 'json',
            data: formData,
            type: 'POST',
            success: function(showData) {
                if(showData.hasOwnProperty('error')) {
                    alert(showData.error);
                } else {
                    var newId = showData.id;
                    $.ajax({
                        url: '/new_association/'+resourceId+'/'+associationId+'/'+id+'/'+newId,
                        dataType: 'json',
                        data: formData,
                        type: 'POST',
                        success: function(showData) {
                            if(showData.hasOwnProperty('error')) {
                                // delete resource
                                var url = '/resources/'+associationId+'/'+newId;
                                $.ajax({
                                    url: url,
                                    dataType: 'json',
                                    type: 'DELETE',
                                    success: function(showData) {
                                        if(showData.hasOwnProperty('error')) {
                                            alert('Warning... '+showData.error);
                                        }
                                    },
                                    error: function() {
                                        alert("An error occurred.");
                                    }
                                });
                                alert(showData.error);
                            } else {
                                if(!listRef || refresh==='refresh') {
                                    if(report) {
                                        //showResourceFunction(originalResourceId,originalId);
                                        $('#inner-results').html('');

                                    } else {
                                        showDiagramFunction(originalId,originalResourceId);
                                    }
                                } else {
                                    if(prepend==='prepend') {
                                        $(listRef).prepend(showData.template);
                                    } else {
                                        $(listRef).html(showData.template);
                                    }
                                    $form.find('input.form-control,textarea,select').val(null).trigger('change');
                                    onShowResourceFunction($(listRef));
                                    $('.resource-new-link').filter(':visible').each(function() {
                                        $(this).next().hide();
                                    });
                                }
                            }
                        },
                        error: function() {
                            alert('An error occurred.');
                        }
                    });
                }
            }
        });

        return false;
    });

    $topElem.find('form.update-association').submit(function(e) {
        e.preventDefault();
        var $form = $(this);
        var resourceId = $form.attr("data-resource");
        var associationId = $form.attr("data-association");
        var associationName = $form.attr("data-association-name-reverse");
        var listRef = $form.attr('data-list-ref');
        var id = $form.attr('data-id');
        var formData = $form.serialize();
        var newId = $form.find('select').val();
        var prepend = $form.attr('data-prepend');
        var report = $form.attr('data-report');
        if (!newId) {
            alert('Please select a valid association.');
            return false;
        }
        var refresh = $form.attr('data-refresh');
        var originalId = $form.attr('data-original-id');
        var originalResourceId = $form.attr('data-original-resource');
        var oldRef = "#node-"+associationId+"-"+newId.toString();
        $.ajax({
            url: '/new_association/'+resourceId+'/'+associationId+'/'+id+'/'+newId,
            dataType: 'json',
            data: formData,
            type: 'POST',
            success: function(showData) {
                var $oldRef = $(oldRef);
                if(!listRef || refresh==='refresh') {
                    if($(showData.template).hasClass('server-error')) {
                        alert('A cycle has been detected. Unable to assign '+associationId+' to '+resourceId);
                    };
                    // check if we are in a report
                    if(report) {
                        //showResourceFunction(originalResourceId, originalId);
                        $('#inner-results').html('');
                    } else {
                        showDiagramFunction(originalId,originalResourceId);
                    }

                } else {

                    if($oldRef.length && $oldRef.find('span[data-association-name]').filter(':first').attr('data-association-name')===associationName) {
                        var template = $(showData.template);
                        if(template.hasClass('server-error')) {
                            var $listRef = $(listRef);
                            $listRef.append(showData.template);
                            onShowResourceFunction($listRef);
                        } else {
                            $oldRef.html($(showData.template).unwrap());
                            onShowResourceFunction($oldRef);
                        }
                    } else {
                        var $listRef = $(listRef);
                        $listRef.find('.server-error').remove();
                        if(prepend==='prepend') {
                            $listRef.prepend(showData.template);
                        } else {
                            $listRef.html(showData.template);
                        }
                        onShowResourceFunction($listRef);
                    }
                    $form.find('select').val(null).trigger('change');
                    $('.resource-new-link').filter(':visible').each(function() {
                        $(this).next().hide();
                    });
                }
            }
        });
        return false;
    });

    $topElem.find('.delete-node').click(function(e) {
        e.stopPropagation();
        e.preventDefault();
        var $this = $(this);
        var id = $this.attr('data-id');
        var name = $this.prev().text();
        var resourceId = $this.attr('data-resource');
        var associationName = $this.attr('data-association');
        var associationId = $this.attr('data-association-id');
        var associationRef = $this.attr('data-association-name');
        var inDiagram = $('#in_diagram_flag');
        var url = '/resources_delete';
        if(confirm('Are you sure you want to unlink '+name+' from it\'s '+associationRef+'?')) {
            $.ajax({
                url: url,
                dataType: 'json',
                type: 'POST',
                data: {
                    associationRef: associationRef,
                    resource: resourceId,
                    id: id,
                    association: associationName,
                    association_id: associationId
                },
                success: function(showData) {
                    if(showData.hasOwnProperty('error')) {
                        alert(showData.error);
                    } else {
                        if(inDiagram && inDiagram.length>0) {
                            showDiagramFunction(inDiagram.attr("data-id"),inDiagram.attr('data-resource'));
                        } else {
                            showResourceFunction(associationName, associationId);
                        }
                    }
                },
                error: function() {
                    alert("An error occurred.");
                }
            });
        }
    });


    $topElem.find('.multiselect-ajax').select2({
        closeOnSelect: true,
        ajax: {
            url: function() { return $(this).attr("data-url"); },
            dataType: "json",
            delay: 100,
            data: function(params) {
                var query = {
                    search: params.term,
                    page: params.page || 1
                };
                return query;
            }
        }
    });


    $topElem.find('.multiselect').select2({
        minimumResultsForSearch: 5,
        closeOnSelect: true
    });

    $topElem.find('.resource-show-link').click(showResourceByClickFunction);
};


var createNewResourceForm = function(resourceId, resourceName, data) {
    var $new = $('<a href="#">(New)</a>');
    var $form = null;
    if(data.hasOwnProperty('new_form')) {
        $form = $(data.new_form);
        $form.submit(function(e) {
            e.preventDefault();
            $.ajax({
                url: '/new/'+resourceId,
                dataType: 'json',
                data: $form.serialize(),
                type: 'POST',
                success: function(showData) {
                    if(showData.hasOwnProperty('error')) {
                        alert(showData.error);
                    } else {
                        var id = showData.id;
                        showResourceFunction(resourceId, id);
                    }
                }
            });
            return false;
        });
        $new.click(function(e) {
            e.stopPropagation();
            e.preventDefault();
            $form.slideToggle();
            return false;
        });
        return $('<div></div>').append($new).append($form);
    } else {
        return $("<span></span>");
    }

};

var showResourceFunction = function(resourceId, id) {
    // check for open tabs
    var $tabId = $('#results .nav.nav-tabs .active.show').filter(':first').attr('id');
    $.ajax({
        url: '/show/'+resourceId+'/'+id,
        dataType: 'json',
        type: 'GET',
        success: function(showData) {
            var $results = $('#results');
            $results.empty();
            $results.hide();
            $results.html(showData.template);
            onShowResourceFunction(($(document.body)));
            var $tab = false;
            if($tabId && $tabId.length>0) {
                $tab = $('#' + $tabId);
            }
            if($tab && $tab.length>0) {
                $tab.trigger('click');
            } else {
                $('#results .nav.nav-tabs .nav-link').filter(':first').trigger('click');
            }
            $results.show();
        }
    });
};


var showResourceByClickFunction = function(e) {
    e.preventDefault();
    var resourceId = $(this).attr('data-resource');
    var id = $(this).attr('data-id')
    showResourceFunction(resourceId, id);
};


var createResourceList = function(resourceId, resourceName, data) {
    var $result = $('<div class="col-12"></div>');
    $result.append('<h3>'+resourceName+'</h3>');
    var $new = createNewResourceForm(resourceId, resourceName, data);
    $result.append($new);
    //$result.append($ul);
    return $result;
};